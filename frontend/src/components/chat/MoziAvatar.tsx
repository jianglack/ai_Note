interface Props {
  size?: 'sm' | 'md';
}

export default function MoziAvatar({ size = 'md' }: Props) {
  const dim = size === 'sm' ? 'w-6 h-6 text-xs' : 'w-8 h-8 text-sm';

  return (
    <div
      className={`${dim} rounded-full flex items-center justify-center shrink-0 font-serif font-bold text-paper-0`}
      style={{
        background: 'linear-gradient(135deg, var(--color-accent) 0%, var(--color-accent-soft) 100%)',
      }}
    >
      墨
    </div>
  );
}
