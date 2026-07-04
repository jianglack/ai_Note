import type { ReactNode } from 'react';

export interface ToolControlRailSection {
  key: string;
  title: string;
  content: ReactNode;
}

interface ToolControlRailProps {
  sections: ToolControlRailSection[];
  footer?: ReactNode;
}

export default function ToolControlRail({ sections, footer }: ToolControlRailProps) {
  return (
    <div className="twb-control-rail">
      {sections.map(section => (
        <section className="twb-rail-section" key={section.key}>
          <h2>{section.title}</h2>
          <div>{section.content}</div>
        </section>
      ))}
      {footer && <div className="twb-rail-footer">{footer}</div>}
    </div>
  );
}
