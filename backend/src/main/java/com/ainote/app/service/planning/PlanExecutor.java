package com.ainote.app.service.planning;

import com.ainote.app.entity.TaskPlan;
import com.ainote.app.entity.SideEffectJournal;
import com.ainote.app.entity.TaskStep;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.TaskStepRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.service.AgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PlanExecutor {

    private static final Logger log = LoggerFactory.getLogger(PlanExecutor.class);
    private static final Pattern RESULT_ID_PATTERN = Pattern.compile("ID[:\\s]*([0-9a-fA-F-]{32,36})");
    private static final int MAX_RECURSION_DEPTH = 3;

    private final AgentService agentService;
    private final PlannerService plannerService;
    private final ReflectionService reflectionService;
    private final StepParamResolver paramResolver;
    private final PlanProgressEmitter progressEmitter;
    private final TaskPlanRepository planRepository;
    private final TaskStepRepository stepRepository;
    private final PlanStepCompletionRecorder stepCompletionRecorder;
    private final UserMemoryRepository userMemoryRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService securityExecutor;
    private final com.ainote.app.service.AgentMetricsService metricsService;

    @Value("${app.planning.max-retries:3}")
    private int maxRetries;

    public PlanExecutor(
            AgentService agentService,
            PlannerService plannerService,
            ReflectionService reflectionService,
            StepParamResolver paramResolver,
            PlanProgressEmitter progressEmitter,
            TaskPlanRepository planRepository,
            TaskStepRepository stepRepository,
            PlanStepCompletionRecorder stepCompletionRecorder,
            UserMemoryRepository userMemoryRepository,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Qualifier("securityExecutor")
            ExecutorService securityExecutor,
            com.ainote.app.service.AgentMetricsService metricsService) {
        this.agentService = agentService;
        this.plannerService = plannerService;
        this.reflectionService = reflectionService;
        this.paramResolver = paramResolver;
        this.progressEmitter = progressEmitter;
        this.planRepository = planRepository;
        this.stepRepository = stepRepository;
        this.stepCompletionRecorder = stepCompletionRecorder;
        this.userMemoryRepository = userMemoryRepository;
        this.objectMapper = objectMapper;
        this.securityExecutor = securityExecutor;
        this.metricsService = metricsService;
    }

    public void execute(String planId, String userId) {
        execute(planId, userId, 0);
    }

    private void execute(String planId, String userId, int depth) {
        if (depth >= MAX_RECURSION_DEPTH) {
            failPlan(planId, userId, depth,
                    "Exceeded max recursion depth (" + MAX_RECURSION_DEPTH + ")");
            return;
        }

        TaskPlan plan = planRepository.findById(planId).orElseThrow();
        plan.setStatus(TaskPlan.STATUS_EXECUTING);
        planRepository.save(plan);
        progressEmitter.emitPlanUpdate(planId, plan.getStatus(), plan.getCompletedSteps(), plan.getTotalSteps());

        List<TaskStep> allSteps = stepRepository.findByPlanIdOrderByStepOrder(plan.getId());

        Map<Integer, TaskStep> completedSteps = allSteps.stream()
                .filter(s -> TaskStep.STATUS_SUCCESS.equals(s.getStatus()))
                .collect(Collectors.toMap(TaskStep::getStepOrder, s -> s));

        // 闂傚倷绀佸﹢閬嶁€﹂崼銉嬪洭顢欓悙顒夊仺婵＄偛顑呯€涒晝浜告惔銊︾厸鐎规搩鍠栭幊蹇涘礋閸撗呯＝濞达絽鎼悵锟犳煕閹扳晛濡块柍褜鍓涚划顖滄崲濞戞瑥绶為柛顐ｇ箘娴犵偓绻濋埛鈧崨顔界彧闂佽鍠楀姗€鎮惧┑鍫氬亾閿濆骸澧伴柣娑栧劦濮婃椽宕ㄦ繝鍛棟婵犻潧鍊瑰鑽ゅ垝閳哄懎绾ч柟瀛樻⒐閻忓啴姊洪棃娑氱濠殿喗鎸冲顐﹀醇閺囩喓鍘介梺闈涱焾閸庨亶顢旈鍛簻闁圭儤鏌ㄦ禒锔姐亜閵婏絽鍔﹀┑鈥崇埣瀹曘劑寮剁捄銊ヨ闂備浇宕甸崑鐐电矙閹达附鍎楀璺烘湰椤洘绻濋棃娑欘棏闁哄鐗楅妵鍕敃椤愩垹绠诲銈嗘尰濡炶棄顫忔繝姘＜婵炴垶鐟ラ崬澶愭⒑闂堟稒顥滈悗娑掓櫊楠炲棝寮崶鈺冩澑闂佸搫鍊哥花閬嶅箰閸愵喗鐓?
        List<List<TaskStep>> waves;
        try {
            waves = buildExecutionWaves(allSteps, completedSteps);
        } catch (IllegalStateException e) {
            failPlan(planId, userId, depth, e.getMessage());
            return;
        }
        log.info("Plan {} decomposed into {} execution waves", planId, waves.size());

        for (List<TaskStep> wave : waves) {
            // 濠电姷顣藉Σ鍛村磻閳ь剟鏌涚€ｎ偅宕岄柡宀嬬磿娴狅妇鎷犻幓鎺懶撴俊鐐€栭幐濠氬箖閸屾氨鏆︽慨妯挎硾缁犳娊鏌￠崒姘珖闁挎洖鍊归悡?
            plan = planRepository.findById(planId).orElseThrow();
            if (TaskPlan.STATUS_FAILED.equals(plan.getStatus())
                    || TaskPlan.STATUS_PAUSED.equals(plan.getStatus())
                    || TaskPlan.STATUS_CANCELLED.equals(plan.getStatus())) {
                log.info("Plan {} is {}, stopping execution", planId, plan.getStatus());
                return;
            }

            // 闂備礁鎼ˇ顐﹀疾濞戞◤娲晝閸屾氨顔呴梺闈涚墕椤︻垳绮堥崟顖涚厱婵犻潧妫楅鈺呮偣娴ｅ湱鍙€闁哄瞼鍠撶划顓炩槈濡紮绱為梺琛″亾濞寸姴顑嗛悡鐔兼煏婵炲灝鍔氭い蹇ｄ簼閵囧嫰骞掗弬澶告勃濡?
            final TaskPlan currentPlan = plan;
            List<TaskStep> executable = collectExecutableSteps(wave, completedSteps);

            if (executable.isEmpty()) {
                boolean hasUnfinishedWork = wave.stream()
                        .anyMatch(s -> !TaskStep.STATUS_SUCCESS.equals(s.getStatus())
                                && !TaskStep.STATUS_SKIPPED.equals(s.getStatus()));
                if (hasUnfinishedWork) {
                    failPlan(planId, userId, depth, "No executable steps remain; dependencies not met");
                    return;
                }
                continue;
            }

            if (executable.size() > 1) {
                log.info("Executing {} independent steps sequentially for plan {}", executable.size(), planId);
            }

            for (TaskStep step : executable) {
                if (isPlanStopped(planId)) {
                    log.info("Plan {} stopped before step {}", planId, step.getStepOrder());
                    return;
                }
                executeStep(step, currentPlan, completedSteps, userId, depth);
                if (!handleStepResult(step, currentPlan, completedSteps)) return;
            }
        }

        plan = planRepository.findById(planId).orElseThrow();
        if (TaskPlan.STATUS_EXECUTING.equals(plan.getStatus())) {
            plan.setStatus(TaskPlan.STATUS_COMPLETED);
            planRepository.save(plan);
            progressEmitter.emitPlanUpdate(planId, plan.getStatus(), plan.getCompletedSteps(), plan.getTotalSteps());
            metricsService.recordPlanCompleted();
            log.info("Plan {} completed successfully", planId);
        }

        cleanupPlanMemory(planId);
    }

    private void failPlan(String planId, String userId, int depth, String errorMessage) {
        TaskPlan plan = planRepository.findById(planId).orElseThrow();
        plan.setStatus(TaskPlan.STATUS_FAILED);
        plan.setErrorMessage(errorMessage);
        planRepository.save(plan);
        progressEmitter.emitPlanUpdate(planId, plan.getStatus(),
                plan.getCompletedSteps(), plan.getTotalSteps());
        metricsService.recordPlanFailed();
        log.error("Plan {} failed for user {}: {} [depth={}/max={}]",
                planId, userId, errorMessage, depth, MAX_RECURSION_DEPTH);
    }

    /**
     * 婵犵數濮伴崹鐓庘枖濞戞埃鍋撳鐓庢珝妤犵偛鍟换婵嬪炊瑜忛、鍛存⒑閸濆嫭澶勭€光偓閹间礁鍚归悗锝庡亝閸欏繑绻濋棃娑橆仼闁告棑闄勯妵鍕償閳ヨ櫕娈婚梺璇″灠閸熸潙鐣烽悢纰辨晢闁逞屽墴閸╂盯寮崼鐔蜂哗濠电偞鍨堕…鍥倶鏉堚晝纾兼い鏂挎惈婵倻鈧鍠氶…鍫モ€﹂妸鈺佺疀妞ゆ梻铏庡Σ鐑芥⒑閸濆嫷妲撮柡鍛矒瀵濡搁妷?false 闂備浇宕甸崑鐐电矙韫囨稑绀夐幖娣妼妗呭┑顔筋焾妞村憡鍒婃總鍛婄厓闁靛鍎遍弳閬嶆煟韫囨搫韬鐐寸墱閳ь剚绋掕摫闁绘挶鍎遍湁闁绘﹢娼у瓭濡?
     */
    private boolean handleStepResult(TaskStep step, TaskPlan plan,
                                     Map<Integer, TaskStep> completedSteps) {
        String planId = plan.getId();
        TaskPlan currentPlan = planRepository.findById(planId).orElseThrow();
        if (TaskPlan.STATUS_FAILED.equals(currentPlan.getStatus())
                || TaskPlan.STATUS_CANCELLED.equals(currentPlan.getStatus())
                || TaskPlan.STATUS_PAUSED.equals(currentPlan.getStatus())) {
            return false;
        }

        if (TaskStep.STATUS_FAILED.equals(step.getStatus())) {
            plan.setStatus(TaskPlan.STATUS_FAILED);
            planRepository.save(plan);
            progressEmitter.emitStepUpdate(planId, step.getStepOrder(), step.getStatus(), step.getDescription());
            progressEmitter.emitPlanUpdate(planId, plan.getStatus(), plan.getCompletedSteps(), plan.getTotalSteps());
            metricsService.recordStepExecution(false);
            metricsService.recordPlanFailed();
            log.error("Plan {} failed at step {}", planId, step.getStepOrder());
            return false;
        }
        if (TaskStep.STATUS_SUCCESS.equals(step.getStatus())) {
            synchronized (completedSteps) {
                completedSteps.put(step.getStepOrder(), step);
            }
            plan.setCompletedSteps(plan.getCompletedSteps() + 1);
            planRepository.save(plan);
            progressEmitter.emitStepUpdate(planId, step.getStepOrder(), step.getStatus(), step.getDescription());
            progressEmitter.emitPlanUpdate(planId, plan.getStatus(), plan.getCompletedSteps(), plan.getTotalSteps());
            metricsService.recordStepExecution(true);
        }
        return true;
    }

    /**
     * 闂備浇顕х换鎰崲閹邦儵娑樷枎閹垮啯鏅㈤梺缁樺姈閹苯鈻撴禒瀣厪闁割偅绻冮ˉ婊堟煟閿曚緡鍤欓柕鍡樺笒椤繈顢橀悩鐢垫毎缂傚倷绀侀崐绋课涘┑鍡欐殾婵°倐鍋撻柣锝囧厴瀹曞爼顢楅埀顒勫储濞嗘挻鈷戦柛娑橈工缁楁帗淇婇锝囨噰鐎规洜鎳撻～婵堟崉閾忚鍚嬮柣鐔哥矋閸ㄥ潡濡存笟鈧獮瀣攽閸愨晝浜欓梻渚€娼х换鍡椢ｉ崨鏉戠；鐟滄棃寮?
     * Wave 0: 闂傚倷绀侀幖顐﹀疮閻楀牊鍙忕€规洖娲﹂钘夘渻鐎ｎ亜顒㈤悘蹇撻叄閺屸€愁吋鎼粹€茬凹闂佷紮缍€妞存悂骞堥妸銉㈡斀闁告洦鍋勬慨锕€顪冮妶鍛搭€楅柛鐔告尦瀵偄顓兼径濠囧敹濠电娀娼уΛ婊堫敊婢跺á鏃堟偐闂堟稐娌梺绋块閵堟悂濡存笟鈧獮瀣晜閼恒儲鐝?
     * Wave 1: 婵犵數鍋涢顓熸叏閹绢喖绠犵€广儱娲﹂钘夘渻鐎ｎ亜顒㈤悘?Wave 0 濠电姵顔栭崰妤冪紦閸ф纾块柣銏犲閺佷線鏌涢幇闈涙灍闁稿骸绉归弻娑㈠即閵娿儱鈷掗梺鍛婃煥鐎氼剝鐏冮梺缁橈供閸犳牠鍩€椤戭剙鎳忛～鏇熺箾閸℃ɑ灏柣顓燁殕閵囧嫰寮介妸銈勫闁诲孩纰嶉悷鈺呭箖鐠鸿　妲堟俊顖濆亹閸斿湱绱?
     * ...婵犵數鍋涢顓熸叏娴兼潙纾块柡灞诲労閺佸棙绻濋棃娑氬ⅱ閻忓繒鏁哥槐鎾存媴婵埈浜幊?
     */
    private List<List<TaskStep>> buildExecutionWaves(List<TaskStep> allSteps,
                                                      Map<Integer, TaskStep> completedSteps) {
        List<List<TaskStep>> waves = new ArrayList<>();
        Set<Integer> scheduled = new HashSet<>(completedSteps.keySet());

        List<TaskStep> remaining = allSteps.stream()
                .filter(s -> !TaskStep.STATUS_SUCCESS.equals(s.getStatus())
                        && !TaskStep.STATUS_SKIPPED.equals(s.getStatus()))
                .collect(Collectors.toList());

        while (!remaining.isEmpty()) {
            List<TaskStep> wave = remaining.stream()
                    .filter(s -> {
                        if (s.getDependsOn() == null || s.getDependsOn().length == 0) return true;
                        return Arrays.stream(s.getDependsOn()).allMatch(scheduled::contains);
                    })
                    .toList();

            if (wave.isEmpty()) {
                String remainingOrders = remaining.stream()
                        .map(s -> String.valueOf(s.getStepOrder()))
                        .collect(Collectors.joining(","));
                throw new IllegalStateException(
                        "Unsatisfied or cyclic step dependencies: remaining steps [" + remainingOrders + "]");
            }

            waves.add(wave);
            wave.forEach(s -> scheduled.add(s.getStepOrder()));
            remaining.removeAll(wave);
        }

        return waves;
    }

    private void cleanupPlanMemory(String planId) {
        try {
            // Clean up step-specific memory keys (plan:{id}:step:{n})
            List<TaskStep> steps = stepRepository.findByPlanIdOrderByStepOrder(planId);
            for (TaskStep step : steps) {
                String stepMemoryKey = "plan:" + planId + ":step:" + step.getStepOrder();
                userMemoryRepository.deleteByUserId(stepMemoryKey);
            }
            // Also clean up legacy plan-level key in case of old data
            userMemoryRepository.deleteByUserId("plan:" + planId);
            log.debug("Cleaned up chat memory for plan {} ({} steps)", planId, steps.size());
        } catch (Exception e) {
            log.warn("Failed to cleanup plan memory for {}: {}", planId, e.getMessage());
        }
    }

    private void executeStep(TaskStep step, TaskPlan plan,
                             Map<Integer, TaskStep> completedSteps,
                             String userId, int depth) {
        step.setStatus(TaskStep.STATUS_IN_PROGRESS);
        step.setStartedAt(LocalDateTime.now());
        stepRepository.save(step);
        progressEmitter.emitStepUpdate(plan.getId(), step.getStepOrder(), step.getStatus(), step.getDescription());

        while (true) {
            try {
                String resolvedParams = paramResolver.resolve(step, completedSteps);
                String prompt = buildStepPrompt(step, resolvedParams, plan.getGoal());

                // Use a step-specific memory key to avoid:
                // 1) corrupting normal chat history
                // 2) race conditions when parallel steps share the same memory key
                String stepMemoryKey = "plan:" + plan.getId() + ":step:" + step.getStepOrder();
                var response = agentService.chatTrustedSystemPrompt(
                        prompt, List.of(), stepMemoryKey, userId,
                        "PlanExecutor", "step_execution");
                String agentResponse = response.getContent();

                if (isPlanStopped(plan.getId())) {
                    markStepCancelled(step, plan.getId());
                    return;
                }

                // AgentService catches exceptions internally and returns error text as content
                // Detect these wrapper error messages
                if (isAgentWrapperError(agentResponse)) {
                    log.warn("Step {} got error response from agent: {}", step.getStepOrder(), agentResponse);
                    Exception wrappedError = new RuntimeException(agentResponse);
                    ReflectionService.Decision errorDecision =
                            reflectionService.reflect(step, agentResponse, wrappedError);
                    if (errorDecision == ReflectionService.Decision.RETRY) {
                        step.setRetryCount(step.getRetryCount() + 1);
                        stepRepository.save(step);
                        if (step.getRetryCount() <= maxRetries) {
                            continue;
                        }
                    }
                    step.setStatus(TaskStep.STATUS_FAILED);
                    step.setErrorMessage(agentResponse);
                    step.setCompletedAt(LocalDateTime.now());
                    stepRepository.save(step);
                    return;
                }

                ReflectionService.Decision decision =
                        reflectionService.reflect(step, agentResponse, null);

                switch (decision) {
                    case CONTINUE -> {
                        step.setStatus(TaskStep.STATUS_SUCCESS);
                        String outputResult = buildOutputResult(agentResponse);
                        step.setOutputResult(outputResult);
                        step.setCompletedAt(LocalDateTime.now());
                        stepCompletionRecorder.recordSuccess(
                                step,
                                buildSideEffectJournal(step, outputResult, agentResponse));
                        return;
                    }
                    case RETRY -> {
                        step.setRetryCount(step.getRetryCount() + 1);
                        stepRepository.save(step);
                        metricsService.recordStepRetry();
                        if (step.getRetryCount() <= maxRetries) {
                            log.info("Retrying step {} (attempt {})",
                                    step.getStepOrder(), step.getRetryCount());
                            continue;
                        }
                        step.setStatus(TaskStep.STATUS_FAILED);
                        step.setErrorMessage("Max retries exceeded");
                        step.setCompletedAt(LocalDateTime.now());
                        stepRepository.save(step);
                        return;
                    }
                    case INSERT_STEP -> {
                        log.info("Inserting prerequisite step before step {}", step.getStepOrder());
                        insertDynamicStep(plan, step, agentResponse);
                        execute(plan.getId(), userId, depth + 1);
                        return;
                    }
                    case REPLAN -> {
                        log.info("Replanning from step {}", step.getStepOrder());
                        step.setStatus(TaskStep.STATUS_FAILED);
                        step.setErrorMessage("Triggered replan");
                        stepRepository.save(step);
                        plannerService.replanRemaining(plan, step, agentResponse, userId);
                        execute(plan.getId(), userId, depth + 1);
                        return;
                    }
                }

            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                log.error("Step {} execution error: {}", step.getStepOrder(), errMsg);

                // Fast-fail on quota/balance errors 闂?retrying won't help
                if (isQuotaError(errMsg)) {
                    log.error("API quota exhausted, failing step immediately");
                    step.setStatus(TaskStep.STATUS_FAILED);
                    step.setErrorMessage(errMsg);
                    step.setCompletedAt(LocalDateTime.now());
                    stepRepository.save(step);
                    return;
                }

                ReflectionService.Decision decision =
                        reflectionService.reflect(step, null, e);
                if (decision == ReflectionService.Decision.RETRY) {
                    step.setRetryCount(step.getRetryCount() + 1);
                    stepRepository.save(step);
                    if (step.getRetryCount() <= maxRetries) {
                        continue;
                    }
                }
                step.setStatus(TaskStep.STATUS_FAILED);
                step.setErrorMessage(errMsg);
                step.setCompletedAt(LocalDateTime.now());
                stepRepository.save(step);
                return;
            }

            break;
        }
    }

    private boolean dependenciesMet(TaskStep step, Map<Integer, TaskStep> completedSteps) {
        if (step.getDependsOn() == null || step.getDependsOn().length == 0) return true;
        for (Integer dep : step.getDependsOn()) {
            if (!completedSteps.containsKey(dep)) return false;
        }
        return true;
    }

    private boolean isPlanStopped(String planId) {
        TaskPlan currentPlan = planRepository.findById(planId).orElseThrow();
        return TaskPlan.STATUS_FAILED.equals(currentPlan.getStatus())
                || TaskPlan.STATUS_CANCELLED.equals(currentPlan.getStatus())
                || TaskPlan.STATUS_PAUSED.equals(currentPlan.getStatus());
    }

    private void markStepCancelled(TaskStep step, String planId) {
        step.setStatus(TaskStep.STATUS_CANCELLED);
        step.setErrorMessage("Plan cancelled");
        step.setCompletedAt(LocalDateTime.now());
        stepRepository.save(step);
        progressEmitter.emitStepUpdate(planId, step.getStepOrder(), step.getStatus(), step.getDescription());
        log.info("Step {} cancelled because plan {} stopped", step.getStepOrder(), planId);
    }

    private List<TaskStep> collectExecutableSteps(List<TaskStep> wave,
                                                  Map<Integer, TaskStep> completedSteps) {
        return wave.stream()
                .filter(s -> !TaskStep.STATUS_SUCCESS.equals(s.getStatus())
                        && !TaskStep.STATUS_SKIPPED.equals(s.getStatus()))
                .filter(s -> {
                    if (!dependenciesMet(s, completedSteps)) {
                        s.setStatus(TaskStep.STATUS_BLOCKED);
                        stepRepository.save(s);
                        log.warn("Step {} blocked: dependencies not met", s.getStepOrder());
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    private String buildStepPrompt(TaskStep step, String resolvedParams, String goal) {
        StringBuilder sb = new StringBuilder();
        sb.append("Execute the following operation as one step in a multi-step plan.\\n\\n");
        sb.append("Action: ").append(step.getAction()).append("\\n");
        sb.append("Description: ").append(step.getDescription()).append("\\n");
        if (resolvedParams != null && !resolvedParams.equals("null")) {
            sb.append("Parameters: ").append(resolvedParams).append("\\n");
        }
        sb.append("\\nUse the available tools directly. After completion, briefly summarize the result.");
        return sb.toString();
    }

    private boolean isAgentWrapperError(String response) {
        return response != null
                && (response.startsWith("\u62b1\u6b49") || response.startsWith("\u93b6\u8fa8\u74d1"));
    }

    private boolean isQuotaError(String message) {
        String lower = message.toLowerCase();
        return lower.contains("\u4f59\u989d\u4e0d\u8db3") || lower.contains("\u8bf7\u5145\u503c")
                || lower.contains("quota") || lower.contains("insufficient")
                || lower.contains("rate limit") || lower.contains("429");
    }

    /**
     * 闂傚倷绀侀幉锟犲蓟閿濆绀夌€广儱顦悞鍨亜閹达絾纭堕柛鏂跨Ф閹叉悂寮堕崹顔芥閻庤娲橀〃濠冧繆閻戣棄唯闁靛鍠楅悗顐ょ磽閸屾艾鈧悂宕板璺虹獥婵°倕鎷嬮弫鍡涙煟閹邦厽鍎楁繛鍫滅矙閺屾洟宕煎┑鍫㈩唺缂備焦鍔栭〃鍫ュ箟?Agent 闂傚倷绀侀幉锟犳偡閿曞倸鍨傜憸鐗堝笧瀹撲線鏌涢妷顔荤暗濞存粌缍婇弻鐔煎箚瑜嶉弳杈ㄣ亜閵堝懏鍣洪柟渚垮妼閳规垿宕熼銏犘曟俊鐐€曟鎼佸疮閺夋嚦娑欑瑹閳ь剟宕洪埀顒併亜閹烘垵鈧綊宕崫鍔藉綊鏁愰崶鍓佸姼闁汇埄鍨崕鐢稿蓟閻旇櫣绠旀繛鎴炆戦敍宥夋煛閳ь剚绂掔€ｎ偆鍘搁柣蹇曞仦閸庤櫕鎱ㄩ崘顔解拺闁告瑥顦遍惌鎺斺偓瑙勬礃缁矂顢樻總绋跨倞闁冲搫鍊婚埀顒夊弮濮婃椽鎮烽幍顔昏檸缂備礁顦壕顓犳閻愬搫绫嶉柛顐ｇ箘閻嫰鏌ｉ悩杈╊槮婵犫偓鏉堚晝鐭?
     * 闂傚倷绶氬鑽ゆ嫻閻旂厧绀夌€光偓閳ь剛妲愰悙纰樺亾閿濆骸鏋涚紒鈧崼鈶╁亾楠炲灝鍔氭俊顐弮瀹曟繈宕熼瀣閹晠顢欑亸鏍т壕闁哄洨鍠愬▍鐘绘煟濡鍤欑紒鐘垫暬閺岀喖鎮滃Ο铏逛淮閻庢鍠栭崯鍧楀煡婢舵劕绠绘い鏍ㄧ煯婢规洖鈹戦悙鏉戠仸闁瑰皷鏅滅粋宥呪攽鐎ｎ亣鎽曟繝鐢靛Т閸嬪棗顭囨导瀛樼厪濠㈣泛妫欏▍鎾绘煃瑜滈崜娆撳疮閹绢喖鏋佺€广儱娲ｅ▽顏堟煕婵犲嫬鏋庨柣婵愬灦閺岋綀绠涢幘鍓侇唶濠碘槅鍋呴〃鍫㈡閻愮鍋撻敐搴℃灈缂佲偓閸懇鍋撻獮鍨姎婵☆偒鍙冨畷婵嬪礋椤愬妫冮幃鈺咁敊鐏忔牕浜炬繝闈涱儏闂傤垶鏌涘┑鍕姢缁惧墽鍋撻妵鍕籍閳ь剙危閹烘梻鐭?PENDING 缂傚倸鍊烽悞锔剧矙閹次诲洭顢涘鍕靛仺闂佺粯鏌ㄩ崥瀣偂閸屾壕鍋撻獮鍨姎婵☆偅鐟╅幃锟犲焵椤掑嫭鈷戦柛锔诲幘閹虫鏌涢埦鈧弲婊堝棘閳ь剟姊?
     */
    private void insertDynamicStep(TaskPlan plan, TaskStep currentStep, String agentResponse) {
        int insertOrder = currentStep.getStepOrder();

        // 闂備浇顕х换鎰崲閹邦儵娑樜旈崘顏嗩槸闁诲函缍嗛崰鏍矆閸懇鍋撻獮鍨姎闁瑰啿閰ｉ獮鎰板Χ婢跺鍘卞┑顔斤供閸撴岸骞戦敐澶嬬厽闁靛骏缍嗛崵娆愩亜椤愮姴鐏插┑锛勫厴閸┾剝绻涢幆褌澹曢梺鍛婂姦閸犳宕?order 闂傚倷绀侀幉锟犳嚌閹灐褰掓憥閸屾粍妲€婵犵數鍋為崹鍫曞箰閹绢喖纾婚柟鎹愮М?
        stepRepository.shiftStepOrders(plan.getId(), insertOrder);
        shiftDependsOnReferences(plan.getId(), insertOrder);

        // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰靛殭婵☆偅锕㈤弻鐔封枔閸喗鐏嶉梺浼欑秮娴滃爼寮诲☉銏犵鐎规洖娉﹂妶澶嬬厽闁靛牆鎳忛ˉ婊勩亜椤愮姴鐏插┑锛勫厴閸┾剝绻涢幆褌澹?
        TaskStep newStep = new TaskStep();
        newStep.setPlanId(plan.getId());
        newStep.setStepOrder(insertOrder);
        newStep.setAction("AUTO_PREREQUISITE");
        newStep.setDescription("Auto prerequisite step: " + extractPrerequisiteDescription(agentResponse));
        newStep.setStatus(TaskStep.STATUS_PENDING);
        newStep.setDependsOn(currentStep.getDependsOn()); // 缂傚倸鍊搁崐椋庣礊閳ь剟鏌涢弴銊ヤ航婵☆偓绻濆娲川婵犲海鍔烽梺鍝ュУ閸旀瑩銆佸Δ鍛€烽柟纰卞幗椤旀棃姊洪棃娑氬婵炶绠撻幃楣冨础閻愵亖鍋撻幒鎴僵妞ゆ挾濮烽悾鍓х磽?
        stepRepository.save(newStep);

        // 闂佽崵鍠愮划搴㈡櫠濡ゅ懎绠伴柛娑橈攻濞呯娀鏌ｅΟ鐑樷枙婵為棿鍗抽弻鏇熷緞閸℃ɑ鐝﹂梺閫炲苯澧柛鐔告尦瀵崵浠︾粵瀣倯闂佹悶鍎弲婊堟倵椤撶喓绠?PENDING闂傚倷鐒︾€笛呯矙閹寸偟闄勯柡鍐ㄥ€归钘夘渻鐎ｎ亜顒㈤悘蹇撻叄閺屸€愁吋閸愩劌顬夐梺缁樻尨閸嬫捇姊绘担鍛婅础缂侇噮鍨抽弫顕€骞掑Δ鈧惌妤呮煕閳╁啰鈯曢柛搴＄Ч閺屾盯寮撮妸銉モ拻闂佸憡鏌ㄧ€氼剝鐏?
        currentStep.setStepOrder(insertOrder + 1);
        currentStep.setStatus(TaskStep.STATUS_PENDING);
        currentStep.setRetryCount(0);
        currentStep.setDependsOn(new Integer[]{insertOrder});
        stepRepository.save(currentStep);

        // 闂傚倷绀侀幖顐⒚洪妶澶嬪仱闁靛ň鏅涢拑鐔封攽閻樻彃顏┑顖氥偢閺屽秹濡烽妷銉︽瘣闂佽绻愰敃顏堝蓟閻旂⒈鏁嗛柛灞剧矊缁椻€愁渻閵堝棗绗ч柛瀣仱閵嗗啴濡烽埡浣勓囨煕閳╁叐鎴︻敊?
        plan.setTotalSteps(plan.getTotalSteps() + 1);
        planRepository.save(plan);

        log.info("Inserted dynamic step at order {} for plan {}, total steps now {}",
                insertOrder, plan.getId(), plan.getTotalSteps());
        progressEmitter.emitPlanUpdate(plan.getId(), plan.getStatus(),
                plan.getCompletedSteps(), plan.getTotalSteps());
    }

    private void shiftDependsOnReferences(String planId, int insertOrder) {
        List<TaskStep> steps = stepRepository.findByPlanIdOrderByStepOrder(planId);
        for (TaskStep step : steps) {
            Integer[] dependsOn = step.getDependsOn();
            if (dependsOn == null || dependsOn.length == 0) {
                continue;
            }

            Integer[] shifted = Arrays.stream(dependsOn)
                    .map(dep -> dep != null && dep >= insertOrder ? dep + 1 : dep)
                    .toArray(Integer[]::new);

            if (!Arrays.equals(dependsOn, shifted)) {
                step.setDependsOn(shifted);
                stepRepository.save(step);
            }
        }
    }

    private String extractPrerequisiteDescription(String agentResponse) {
        if (agentResponse == null || agentResponse.isBlank()) {
            return "Execute prerequisite operation";
        }
        String compact = agentResponse.replaceAll("\\s+", " ").trim();
        return compact.length() > 80 ? compact.substring(0, 80) + "..." : compact;
    }

    private String buildOutputResult(String agentResponse) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("response", agentResponse);
            Matcher idMatcher = RESULT_ID_PATTERN.matcher(agentResponse == null ? "" : agentResponse);
            if (idMatcher.find()) {
                String id = idMatcher.group(1);
                result.put("id", id);
                result.put("noteId", id);
                result.put("noteIds", List.of(id));
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            String safeResponse = agentResponse == null ? "" : agentResponse;
            var fallback = objectMapper.getNodeFactory().objectNode();
            fallback.put("response", safeResponse);
            return fallback.toString();
        }
    }

    private Optional<SideEffectJournal> buildSideEffectJournal(
            TaskStep step, String outputResult, String agentResponse) {
        String action = step.getAction();
        if (action == null || action.isBlank()) {
            return Optional.empty();
        }

        String normalizedAction = action.toLowerCase(Locale.ROOT);
        if (isReadOnlyAction(normalizedAction)) {
            return Optional.empty();
        }

        Optional<String> resourceId = extractResourceId(outputResult);
        String rollbackAction = inferRollbackAction(normalizedAction);
        boolean executable = rollbackAction != null
                && isRollbackExecutable(rollbackAction, resourceId);

        Map<String, Object> journal = new LinkedHashMap<>();
        journal.put("version", 1);
        journal.put("type", "SIDE_EFFECT_JOURNAL");
        journal.put("originalAction", action);
        journal.put("stepOrder", step.getStepOrder());
        journal.put("executable", executable);
        if (rollbackAction != null) {
            journal.put("rollbackAction", rollbackAction);
        }
        resourceId.ifPresent(id -> journal.put("resourceId", id));
        journal.put("description", describeRollback(rollbackAction, action, resourceId));
        if (!executable) {
            journal.put("reason", rollbackAction == null
                    ? "No deterministic rollback action is known for this step"
                    : "Rollback requires state that was not captured in the step output");
        }
        journal.put("outputResult", parseOutputResult(outputResult, agentResponse));

        try {
            SideEffectJournal sideEffectJournal = new SideEffectJournal();
            sideEffectJournal.setPlanId(step.getPlanId());
            sideEffectJournal.setStepId(step.getId());
            sideEffectJournal.setStepOrder(step.getStepOrder());
            sideEffectJournal.setVersion(1);
            sideEffectJournal.setOriginalAction(action);
            sideEffectJournal.setRollbackAction(rollbackAction);
            sideEffectJournal.setResourceId(resourceId.orElse(null));
            sideEffectJournal.setExecutable(executable);
            sideEffectJournal.setReason(!executable
                    ? (String) journal.get("reason")
                    : null);
            sideEffectJournal.setOutputSnapshot(outputResult);
            sideEffectJournal.setStatus(SideEffectJournal.STATUS_PENDING);
            sideEffectJournal.setJournalJson(objectMapper.writeValueAsString(journal));
            return Optional.of(sideEffectJournal);
        } catch (Exception e) {
            log.warn("Failed to build side-effect journal for step {}: {}",
                    step.getStepOrder(), e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isReadOnlyAction(String action) {
        return action.contains("search")
                || action.contains("list")
                || action.contains("read")
                || action.contains("get")
                || action.contains("query")
                || action.contains("analyze")
                || action.contains("summarize")
                || action.contains("evaluate");
    }

    private Optional<String> extractResourceId(String outputResult) {
        if (outputResult == null || outputResult.isBlank()) {
            return Optional.empty();
        }
        try {
            var root = objectMapper.readTree(outputResult);
            for (String field : List.of("id", "noteId", "folderId", "scheduleId", "mediaId", "workflowId")) {
                if (root.hasNonNull(field) && root.get(field).isTextual()) {
                    return Optional.of(root.get(field).asText());
                }
            }
            if (root.has("noteIds") && root.get("noteIds").isArray() && root.get("noteIds").size() > 0) {
                var first = root.get("noteIds").get(0);
                if (first.isTextual()) {
                    return Optional.of(first.asText());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse step output for compensation journal: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private String inferRollbackAction(String action) {
        if (action.contains("permanent") || action.contains("empty_trash")) {
            return null;
        }
        if (action.contains("addtag") || (action.contains("tag") && action.contains("add"))) {
            return "REMOVE_TAG";
        }
        if (action.contains("create") || action.contains("new") || action.contains("add")) {
            return "DELETE_CREATED_RESOURCE";
        }
        if (action.contains("delete") || action.contains("remove")) {
            return "RESTORE_DELETED_RESOURCE";
        }
        if (action.contains("move")) {
            return "MOVE_RESOURCE_BACK";
        }
        if (action.contains("update") || action.contains("edit") || action.contains("modify")) {
            return "RESTORE_PREVIOUS_STATE";
        }
        return null;
    }

    private boolean isRollbackExecutable(String rollbackAction, Optional<String> resourceId) {
        if ("RESTORE_PREVIOUS_STATE".equals(rollbackAction)) {
            return false;
        }
        return switch (rollbackAction) {
            case "DELETE_CREATED_RESOURCE", "RESTORE_DELETED_RESOURCE", "MOVE_RESOURCE_BACK", "REMOVE_TAG" ->
                    resourceId.isPresent();
            default -> false;
        };
    }

    private String describeRollback(String rollbackAction, String originalAction, Optional<String> resourceId) {
        if (rollbackAction == null) {
            return "Manual review required for action " + originalAction;
        }
        return rollbackAction + " for " + originalAction
                + resourceId.map(id -> " on resource " + id).orElse("");
    }

    private Object parseOutputResult(String outputResult, String agentResponse) {
        if (outputResult == null || outputResult.isBlank()) {
            return Map.of("response", agentResponse == null ? "" : agentResponse);
        }
        try {
            return objectMapper.readValue(outputResult, Map.class);
        } catch (Exception e) {
            return Map.of("response", agentResponse == null ? "" : agentResponse);
        }
    }
}
