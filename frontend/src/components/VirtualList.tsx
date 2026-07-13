import {
  forwardRef,
  useImperativeHandle,
  useMemo,
  useRef,
  type CSSProperties,
  type Key,
  type ReactElement,
  type ReactNode,
  type Ref,
} from 'react';
import { List, type ListImperativeAPI, type RowComponentProps } from 'react-window';

export type LegacyListChildComponentProps<T = undefined> = {
  data: T;
  index: number;
  style: CSSProperties;
};

export type LegacyListHandle = {
  resetAfterIndex: (index?: number, shouldForceUpdate?: boolean) => void;
  scrollToItem: (
    index: number,
    align?: 'auto' | 'center' | 'end' | 'smart' | 'start',
  ) => void;
};

type LegacyListRenderer<T> = (props: LegacyListChildComponentProps<T>) => ReactNode;

type SharedLegacyListProps<T> = {
  children: LegacyListRenderer<T>;
  className?: string;
  height: number;
  itemCount: number;
  itemData?: T;
  itemKey?: (index: number, data: T) => Key;
  style?: CSSProperties;
  width: number | string;
};

export type FixedSizeListProps<T = undefined> = SharedLegacyListProps<T> & {
  itemSize: number;
};

export type VariableSizeListProps<T = undefined> = SharedLegacyListProps<T> & {
  itemSize: (index: number) => number;
};

type RowProps<T> = {
  itemData: T;
  itemKey?: (index: number, data: T) => Key;
  itemRenderer: LegacyListRenderer<T>;
};

function LegacyRow<T>({
  index,
  itemData,
  itemRenderer,
  style,
}: RowComponentProps<RowProps<T>>) {
  return <>{itemRenderer({ data: itemData, index, style })}</>;
}

function useLegacyListHandle(
  ref: Ref<LegacyListHandle>,
  listRef: React.MutableRefObject<ListImperativeAPI | null>,
) {
  useImperativeHandle(ref, () => ({
    resetAfterIndex: () => {
      // react-window v2 recalculates from row props/row height changes.
    },
    scrollToItem: (index, align = 'auto') => {
      listRef.current?.scrollToRow({ index, align });
    },
  }), [listRef]);
}

function listStyle(height: number, width: number | string, style?: CSSProperties): CSSProperties {
  return {
    height,
    width,
    ...style,
  };
}

function FixedSizeListInner<T = undefined>(
  {
    children,
    className,
    height,
    itemCount,
    itemData,
    itemKey,
    itemSize,
    style,
    width,
  }: FixedSizeListProps<T>,
  ref: Ref<LegacyListHandle>,
) {
  const listRef = useRef<ListImperativeAPI | null>(null);
  useLegacyListHandle(ref, listRef);
  const rowProps = useMemo<RowProps<T>>(() => ({
    itemData: itemData as T,
    itemKey,
    itemRenderer: children,
  }), [children, itemData, itemKey]);

  return (
    <List
      className={className}
      defaultHeight={height}
      listRef={listRef}
      rowComponent={LegacyRow}
      rowCount={itemCount}
      rowHeight={itemSize}
      rowProps={rowProps}
      style={listStyle(height, width, style)}
    />
  );
}

function VariableSizeListInner<T = undefined>(
  {
    children,
    className,
    height,
    itemCount,
    itemData,
    itemKey,
    itemSize,
    style,
    width,
  }: VariableSizeListProps<T>,
  ref: Ref<LegacyListHandle>,
) {
  const listRef = useRef<ListImperativeAPI | null>(null);
  useLegacyListHandle(ref, listRef);
  const rowProps = useMemo<RowProps<T>>(() => ({
    itemData: itemData as T,
    itemKey,
    itemRenderer: children,
  }), [children, itemData, itemKey]);

  return (
    <List
      className={className}
      defaultHeight={height}
      listRef={listRef}
      rowComponent={LegacyRow}
      rowCount={itemCount}
      rowHeight={itemSize}
      rowProps={rowProps}
      style={listStyle(height, width, style)}
    />
  );
}

export const FixedSizeList = forwardRef(FixedSizeListInner) as <T = undefined>(
  props: FixedSizeListProps<T> & { ref?: Ref<LegacyListHandle> },
) => ReactElement;

export const VariableSizeList = forwardRef(VariableSizeListInner) as <T = undefined>(
  props: VariableSizeListProps<T> & { ref?: Ref<LegacyListHandle> },
) => ReactElement;
