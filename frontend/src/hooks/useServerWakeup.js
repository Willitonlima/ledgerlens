import { useCallback, useState } from "react";

export default function useServerWakeup() {
  const [serverWaking, setServerWaking] = useState(false);
  const showWakeupMessage = useCallback(() => setServerWaking(true), []);
  const clearWakeupMessage = useCallback(() => setServerWaking(false), []);
  return { serverWaking, showWakeupMessage, clearWakeupMessage };
}
