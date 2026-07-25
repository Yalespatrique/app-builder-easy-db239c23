import {
  useFocusable,
  type FocusableComponentLayout,
  type FocusDetails,
} from "@noriginmedia/norigin-spatial-navigation";
import {
  cloneElement,
  isValidElement,
  useCallback,
  type ReactElement,
} from "react";

type Props = {
  onEnterPress?: (props: object, details: FocusDetails) => void;
  onFocus?: (
    layout: FocusableComponentLayout,
    props: object,
    details: FocusDetails,
  ) => void;
  focusKey?: string;
  autoFocus?: boolean;
  className?: string;
  children:
    | ReactElement<{
        ref?: unknown;
        "data-focused"?: string;
        className?: string;
      }>
    | ((focused: boolean) => ReactElement);
};

/**
 * Thin wrapper around norigin useFocusable that:
 *  - wires ref + focused state onto the child
 *  - toggles data-focused="true" so CSS (.aster-focusable) reacts
 *  - auto-focuses on mount when requested
 */
export function Focusable({
  onEnterPress,
  onFocus,
  focusKey,
  autoFocus,
  className,
  children,
}: Props) {
  const { ref, focused, focusSelf } = useFocusable({
    onEnterPress,
    onFocus,
    focusKey,
  });

  const setRef = useCallback(
    (node: HTMLElement | null) => {
      (ref as { current: HTMLElement | null }).current = node;
      if (autoFocus && node) focusSelf();
    },
    [ref, autoFocus, focusSelf],
  );

  if (typeof children === "function") {
    const rendered = children(focused);
    return cloneElement(rendered, {
      ref: setRef,
      "data-focused": focused ? "true" : "false",
      className: [rendered.props.className, className]
        .filter(Boolean)
        .join(" "),
    });
  }

  if (!isValidElement(children)) return null;
  return cloneElement(children, {
    ref: setRef,
    "data-focused": focused ? "true" : "false",
    className: [children.props.className, "aster-focusable", className]
      .filter(Boolean)
      .join(" "),
  });
}
