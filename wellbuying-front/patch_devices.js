const fs = require('fs');
const path = require('path');

const tokenFile = path.join(__dirname, 'src/lib/auth/token-storage.ts');
let tokenContent = fs.readFileSync(tokenFile, 'utf8');

if (!tokenContent.includes('export function getCachedDevices')) {
  tokenContent += `
const DEVICES_CACHE_KEY = "wb.devicesCache";

export function getCachedDevices(): any[] | null {
  if (!isBrowser()) return null;
  const data = window.localStorage.getItem(DEVICES_CACHE_KEY);
  if (!data) return null;
  try {
    return JSON.parse(data);
  } catch {
    return null;
  }
}

export function saveCachedDevices(devices: any[]): void {
  if (!isBrowser()) return;
  window.localStorage.setItem(DEVICES_CACHE_KEY, JSON.stringify(devices));
}
`;
  fs.writeFileSync(tokenFile, tokenContent);
}

const profileFile = path.join(__dirname, 'src/components/account/ProfileContent.tsx');
let profileContent = fs.readFileSync(profileFile, 'utf8');

if (!profileContent.includes('getCachedDevices')) {
  profileContent = profileContent.replace(
    'import { clearTokens, getDeviceId } from "@/lib/auth/token-storage";',
    'import { clearTokens, getDeviceId, getCachedDevices, saveCachedDevices } from "@/lib/auth/token-storage";'
  );
  
  const oldDeviceLogic = `  useEffect(() => {
    getDevices()
      .then(setDevices)
      .catch(() => setDevices([]));
  }, []);`;
  
  const newDeviceLogic = `  useEffect(() => {
    getDevices()
      .then((data) => {
        if (data && data.length > 0) {
          saveCachedDevices(data);
          setDevices(data);
        } else {
          // 백엔드 세션이 날아갔지만 JWT는 살아있어 빈 배열이 올 경우 캐시 사용
          const cached = getCachedDevices();
          setDevices(cached || []);
        }
      })
      .catch(() => {
        const cached = getCachedDevices();
        setDevices(cached || []);
      });
  }, []);`;
  
  profileContent = profileContent.replace(oldDeviceLogic, newDeviceLogic);
  fs.writeFileSync(profileFile, profileContent);
}
