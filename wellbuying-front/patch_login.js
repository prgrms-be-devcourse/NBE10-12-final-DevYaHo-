const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, 'src/app/login/page.tsx');
let content = fs.readFileSync(file, 'utf8');

// 1. Remove the tabs
const tabsRegex = /<div className="mb-5 grid grid-cols-2 gap-1 rounded-lg bg-wb-tag-surface p-1">[\s\S]*?<\/div>\s*<div className="mb-4">/m;
content = content.replace(tabsRegex, '<div className="mb-4">');

// 2. Add icons and layout for social providers
content = content.replace(
  'import { TextField } from "@/components/ui/TextField";',
  'import { TextField } from "@/components/ui/TextField";\nimport { MessageCircle } from "lucide-react";' // Assuming MessageCircle for Kakao, or we can just use text/colors for now.
);

// We need an SVG for Google. Let's just use generic shapes or styled buttons with initials if we don't have SVGs.
// Or we can just use the provider name. "동그란 아이콘으로 바꿔주고"
const socialBlockOld = `<div className="space-y-2">
            {SOCIAL_PROVIDERS.map(({ provider, label }) => (
              <Button
                key={provider}
                type="button"
                variant="secondary"
                className="w-full"
                onClick={() => {
                  window.location.href = getOAuthAuthorizationUrl(provider);
                }}
              >
                {label}
              </Button>
            ))}
          </div>`;

const socialBlockNew = `<div className="flex justify-center gap-4">
            {SOCIAL_PROVIDERS.map(({ provider, label }) => (
              <button
                key={provider}
                type="button"
                onClick={() => {
                  window.location.href = getOAuthAuthorizationUrl(provider);
                }}
                className={\`flex h-12 w-12 items-center justify-center rounded-full text-xs font-bold shadow-sm transition-opacity hover:opacity-80 \${
                  provider === "KAKAO" ? "bg-[#FEE500] text-black" : "border border-wb-line bg-white text-wb-ink"
                }\`}
                aria-label={label}
              >
                {provider === "KAKAO" ? "K" : "G"}
              </button>
            ))}
          </div>
          
          <div className="mt-8 text-center text-sm text-wb-secondary">
            {mode === "login" ? (
              <>
                아직 계정이 없으신가요?{" "}
                <button
                  type="button"
                  onClick={() => {
                    setMode("signup");
                    setError(null);
                    setNotice(null);
                  }}
                  className="font-bold text-wb-green hover:underline"
                >
                  회원가입하기
                </button>
              </>
            ) : (
              <>
                이미 계정이 있으신가요?{" "}
                <button
                  type="button"
                  onClick={() => {
                    setMode("login");
                    setError(null);
                    setNotice(null);
                  }}
                  className="font-bold text-wb-green hover:underline"
                >
                  로그인하기
                </button>
              </>
            )}
          </div>`;

content = content.replace(socialBlockOld, socialBlockNew);

fs.writeFileSync(file, content);
