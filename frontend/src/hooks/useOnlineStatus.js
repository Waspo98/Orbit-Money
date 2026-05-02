import { useEffect, useState } from 'react';

function readOnlineStatus() {
  return typeof navigator === 'undefined' ? true : navigator.onLine !== false;
}

export function useOnlineStatus() {
  const [isOnline, setIsOnline] = useState(readOnlineStatus);

  useEffect(() => {
    function update() {
      setIsOnline(readOnlineStatus());
    }

    window.addEventListener('online', update);
    window.addEventListener('offline', update);
    return () => {
      window.removeEventListener('online', update);
      window.removeEventListener('offline', update);
    };
  }, []);

  return isOnline;
}

