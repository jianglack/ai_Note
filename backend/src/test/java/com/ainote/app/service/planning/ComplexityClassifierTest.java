package com.ainote.app.service.planning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComplexityClassifierTest {

    private final ComplexityClassifier classifier = new ComplexityClassifier();

    @Test
    void classifiesMultiStepNoteOperationsAsComplexEvenWhenTitleContainsStudy() {
        String query = """
                请只操作标题以【测试】开头的笔记，不要修改其他已有笔记。请帮我完成一个复杂的笔记整理任务：
                新建一个文件夹，名称为【测试-复杂操作演示】；然后新建三篇笔记，第一篇标题为【测试】ReAct Agent 学习记录；
                创建完成后，请把这三篇笔记都移动到【测试-复杂操作演示】文件夹中，并分别添加标签【Agent】、【RAG】、【上下文工程】。
                随后请搜索这三篇测试笔记，提取它们的共同主题，生成一篇新的汇总笔记。
                """;

        assertThat(classifier.classify(query)).isEqualTo(ComplexityClassifier.Complexity.COMPLEX);
    }

    @Test
    void keepsAdvisoryLearningQuestionSimple() {
        assertThat(classifier.classify("我应该如何学习 ReAct Agent，有什么建议？"))
                .isEqualTo(ComplexityClassifier.Complexity.SIMPLE);
    }
}
