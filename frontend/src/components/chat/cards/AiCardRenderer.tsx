import type { CardState } from './types';
import MultiSelectCard from './MultiSelectCard';
import SingleSelectCard from './SingleSelectCard';
import TextInputCard from './TextInputCard';
import ScheduleCard from './ScheduleCard';
import SuggestionMini from './SuggestionMini';
import ConfirmCard from './ConfirmCard';
import TagPickerCard from './TagPickerCard';

interface Props {
  card: CardState;
  onAction: (action: string, data?: unknown) => void;
}

export default function AiCardRenderer({ card, onAction }: Props) {
  const { payload, status, result } = card;
  const collapsed = status !== 'pending';

  switch (payload.kind) {
    case 'multi-select':
      return (
        <MultiSelectCard
          title={payload.title}
          meta={payload.meta}
          icon={payload.icon}
          items={payload.items}
          defaultSelected={payload.defaultSelected}
          confirmLabel={payload.confirmLabel}
          collapsed={collapsed}
          result={result}
          onConfirm={(ids) => onAction(payload.action, { selectedIds: ids })}
          onCancel={() => onAction('cancel')}
        />
      );

    case 'single-select':
      return (
        <SingleSelectCard
          title={payload.title}
          meta={payload.meta}
          icon={payload.icon}
          items={payload.items}
          defaultSelected={payload.defaultSelected}
          collapsed={collapsed}
          result={result}
          onConfirm={(id) => onAction(payload.action, { selectedId: id })}
          onCancel={() => onAction('cancel')}
        />
      );

    case 'text-input':
      return (
        <TextInputCard
          title={payload.title}
          meta={payload.meta}
          initialTitle={payload.initialTitle}
          initialNotebook={payload.initialNotebook}
          initialTags={payload.initialTags}
          collapsed={collapsed}
          result={result}
          onConfirm={(data) => onAction(payload.action, data)}
          onCancel={() => onAction('cancel')}
        />
      );

    case 'schedule':
      return (
        <ScheduleCard
          title={payload.title}
          meta={payload.meta}
          initial={payload.initial}
          collapsed={collapsed}
          result={result}
          onConfirm={(data) => onAction(payload.action, data)}
          onCancel={() => onAction('cancel')}
        />
      );

    case 'suggestion':
      return (
        <SuggestionMini
          kind={payload.suggestionKind}
          text={payload.text}
          buttons={payload.buttons}
          onAction={(action) => onAction(action)}
        />
      );

    case 'confirm':
      return (
        <ConfirmCard
          title={payload.title}
          description={payload.description}
          confirmLabel={payload.confirmLabel}
          collapsed={collapsed}
          result={result}
          onConfirm={() => onAction(payload.action, payload.params)}
          onCancel={() => onAction('cancel')}
        />
      );

    case 'tag-picker':
      return (
        <TagPickerCard
          title={payload.title}
          noteName={payload.noteName}
          tags={payload.tags}
          defaultSelected={payload.defaultSelected}
          collapsed={collapsed}
          result={result}
          onConfirm={(selected) => onAction(payload.action, { noteId: payload.noteId, tags: selected })}
          onCancel={() => onAction('cancel')}
        />
      );

    default:
      return null;
  }
}
