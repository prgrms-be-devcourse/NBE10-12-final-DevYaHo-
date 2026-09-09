const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, 'src/app/login/page.tsx');
let content = fs.readFileSync(file, 'utf8');

// 1. Add passwordConfirm state
content = content.replace(
  'const [password, setPassword] = useState("");',
  'const [password, setPassword] = useState("");\n  const [passwordConfirm, setPasswordConfirm] = useState("");'
);

// 2. Update canSubmitSignup
content = content.replace(
  'password.length >= 8 &&',
  'password.length >= 8 &&\n    password === passwordConfirm &&'
);

// 3. Add Name and Password Confirm to signup mode
// We'll replace the `{mode === "signup" ? ( <div className="space-y-2">` part
const signupStart = '{mode === "signup" ? (\n              <div className="space-y-2">';
const signupStartNew = `{mode === "signup" ? (
              <>
                <TextField
                  label="이름"
                  placeholder="홍길동"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                />
                <div className="space-y-2">`;
content = content.replace(signupStart, signupStartNew);

const passwordBlock = `<TextField
              label="비밀번호"
              type="password"
              placeholder={mode === "signup" ? "8자 이상 입력" : "비밀번호"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />`;
const passwordBlockNew = `<TextField
              label="비밀번호"
              type="password"
              placeholder={mode === "signup" ? "8자 이상 입력" : "비밀번호"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            {mode === "signup" && (
              <TextField
                label="비밀번호 확인"
                type="password"
                placeholder="비밀번호 재입력"
                value={passwordConfirm}
                onChange={(e) => setPasswordConfirm(e.target.value)}
                required
              />
            )}`;
content = content.replace(passwordBlock, passwordBlockNew);

const signupEndFix = `              </div>\n            ) : (\n              <TextField`;
const signupEndFixNew = `              </div>\n              </>\n            ) : (\n              <TextField`;
content = content.replace(signupEndFix, signupEndFixNew);

// 4. Hide social icons for signup
const socialIconsBlock = `<div className="my-5 flex items-center gap-3">
            <div className="h-px flex-1 bg-wb-line" />
            <span className="text-xs text-wb-secondary">또는</span>
            <div className="h-px flex-1 bg-wb-line" />
          </div>

          <div className="flex justify-center gap-4">
            {SOCIAL_PROVIDERS.map(({ provider, label }) => (
              <button`;
const socialIconsBlockNew = `{mode === "login" && (
            <>
              <div className="my-5 flex items-center gap-3">
                <div className="h-px flex-1 bg-wb-line" />
                <span className="text-xs text-wb-secondary">또는</span>
                <div className="h-px flex-1 bg-wb-line" />
              </div>

              <div className="flex justify-center gap-4">
                {SOCIAL_PROVIDERS.map(({ provider, label }) => (
                  <button`;
content = content.replace(socialIconsBlock, socialIconsBlockNew);

const socialIconsEnd = `</button>
            ))}
          </div>`;
const socialIconsEndNew = `</button>
                ))}
              </div>
            </>
          )}`;
content = content.replace(socialIconsEnd, socialIconsEndNew);

fs.writeFileSync(file, content);
