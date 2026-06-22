interface Props {
  checked: boolean;
  indeterminate?: boolean;
  disabled?: boolean;
  onChange?: () => void;
}

export default function WorkflowCheckbox({ checked, indeterminate, disabled, onChange }: Props) {
  const active = checked || indeterminate;

  if (disabled) {
    return (
      <span
        className="shrink-0"
        style={{
          width: 16, height: 16, borderRadius: 4,
          border: '1.5px solid var(--color-border-strong, rgba(74,64,50,0.22))',
          background: 'var(--color-paper-2, #ede5d1)',
          opacity: 0.4,
        }}
      />
    );
  }

  return (
    <button
      type="button"
      onClick={onChange}
      className="shrink-0"
      style={{
        width: 16, height: 16, borderRadius: 4,
        border: active
          ? '1.5px solid var(--color-accent, #b8452e)'
          : '1.5px solid var(--color-border-strong, rgba(74,64,50,0.22))',
        background: active
          ? 'var(--color-accent, #b8452e)'
          : 'var(--color-paper-0, #fbf7ee)',
        cursor: 'pointer',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: 0,
        transition: 'all 0.15s',
      }}
    >
      {checked && !indeterminate && (
        <svg width="10" height="8" viewBox="0 0 10 8" fill="none">
          <path d="M2 5L4.5 7.5L8 3" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )}
      {indeterminate && (
        <svg width="8" height="2" viewBox="0 0 8 2" fill="none">
          <path d="M1 1H7" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      )}
    </button>
  );
}
