const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, 'src/app/login/page.tsx');
let content = fs.readFileSync(file, 'utf8');

// 1. Replace email state with split states
content = content.replace(
  'const [email, setEmail] = useState("");',
  `const [emailId, setEmailId] = useState("");
  const [emailDomain, setEmailDomain] = useState("naver.com");
  const [customDomain, setCustomDomain] = useState("");
  
  const email = \`\${emailId}@\${emailDomain === "custom" ? customDomain : emailDomain}\`;`
);

// We need a helper to reset verification if email changes
// Previous logic: onChange => setEmail, if verified reset.
// Let's create an onChange helper for email parts.
const emailHelper = `  function handleEmailPartChange() {
    if (emailVerified || codeSent) resetEmailVerification();
  }`;

content = content.replace(
  'const [password, setPassword] = useState("");',
  `${emailHelper}\n\n  const [password, setPassword] = useState("");`
);

// 2. Replace the email input in signup mode
const oldSignupEmail = `<div className="flex-1">
                    <TextField
                      label="이메일"
                      type="email"
                      placeholder="name@example.com"
                      value={email}
                      disabled={emailVerified}
                      onChange={(e) => {
                        setEmail(e.target.value);
                        if (emailVerified || codeSent) resetEmailVerification();
                      }}
                      required
                    />
                  </div>`;

const EmailUI = `<div className="flex flex-1 flex-col gap-1.5">
                    <span className="text-xs font-bold text-wb-ink">이메일</span>
                    <div className="flex items-center gap-1.5">
                      <input
                        type="text"
                        className="h-11 w-full min-w-[80px] flex-1 rounded-lg border border-wb-line bg-wb-canvas px-3 text-sm focus:border-wb-green focus:outline-none focus:ring-1 focus:ring-wb-green disabled:opacity-60"
                        placeholder="아이디"
                        value={emailId}
                        disabled={emailVerified}
                        onChange={(e) => {
                          setEmailId(e.target.value);
                          handleEmailPartChange();
                        }}
                        required
                      />
                      <span className="text-sm font-semibold text-wb-secondary">@</span>
                      {emailDomain === "custom" ? (
                        <input
                          type="text"
                          className="h-11 w-full min-w-[90px] flex-1 rounded-lg border border-wb-line bg-wb-canvas px-3 text-sm focus:border-wb-green focus:outline-none focus:ring-1 focus:ring-wb-green disabled:opacity-60"
                          placeholder="직접입력"
                          value={customDomain}
                          disabled={emailVerified}
                          onChange={(e) => {
                            setCustomDomain(e.target.value);
                            handleEmailPartChange();
                          }}
                          required
                        />
                      ) : (
                        <select
                          className="h-11 w-full min-w-[100px] flex-1 rounded-lg border border-wb-line bg-wb-canvas px-2 text-sm focus:border-wb-green focus:outline-none focus:ring-1 focus:ring-wb-green disabled:opacity-60"
                          value={emailDomain}
                          disabled={emailVerified}
                          onChange={(e) => {
                            setEmailDomain(e.target.value);
                            handleEmailPartChange();
                          }}
                        >
                          <option value="naver.com">naver.com</option>
                          <option value="gmail.com">gmail.com</option>
                          <option value="daum.net">daum.net</option>
                          <option value="hanmail.net">hanmail.net</option>
                          <option value="custom">직접입력</option>
                        </select>
                      )}
                    </div>
                  </div>`;

content = content.replace(oldSignupEmail, EmailUI);

// 3. Replace the email input in login mode
const oldLoginEmail = `<TextField
                label="이메일"
                type="email"
                placeholder="name@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />`;

const LoginEmailUI = `<div className="flex flex-col gap-1.5">
                <span className="text-xs font-bold text-wb-ink">이메일</span>
                <div className="flex items-center gap-1.5">
                  <input
                    type="text"
                    className="h-11 w-full min-w-[80px] flex-1 rounded-lg border border-wb-line bg-wb-canvas px-3 text-sm focus:border-wb-green focus:outline-none focus:ring-1 focus:ring-wb-green"
                    placeholder="아이디"
                    value={emailId}
                    onChange={(e) => setEmailId(e.target.value)}
                    required
                  />
                  <span className="text-sm font-semibold text-wb-secondary">@</span>
                  {emailDomain === "custom" ? (
                    <input
                      type="text"
                      className="h-11 w-full min-w-[90px] flex-1 rounded-lg border border-wb-line bg-wb-canvas px-3 text-sm focus:border-wb-green focus:outline-none focus:ring-1 focus:ring-wb-green"
                      placeholder="직접입력"
                      value={customDomain}
                      onChange={(e) => setCustomDomain(e.target.value)}
                      required
                    />
                  ) : (
                    <select
                      className="h-11 w-full min-w-[100px] flex-1 rounded-lg border border-wb-line bg-wb-canvas px-2 text-sm focus:border-wb-green focus:outline-none focus:ring-1 focus:ring-wb-green"
                      value={emailDomain}
                      onChange={(e) => setEmailDomain(e.target.value)}
                    >
                      <option value="naver.com">naver.com</option>
                      <option value="gmail.com">gmail.com</option>
                      <option value="daum.net">daum.net</option>
                      <option value="hanmail.net">hanmail.net</option>
                      <option value="custom">직접입력</option>
                    </select>
                  )}
                </div>
              </div>`;

content = content.replace(oldLoginEmail, LoginEmailUI);

// Fix canSubmitLogin logic
// currently: const canSubmitLogin = email.length > 0 && password.length > 0;
// With custom domain, email.length is always > 0 because of @.
// We need a better check:
content = content.replace(
  'const canSubmitLogin = email.length > 0 && password.length > 0;',
  'const canSubmitLogin = emailId.length > 0 && (emailDomain !== "custom" || customDomain.length > 0) && password.length > 0;'
);

fs.writeFileSync(file, content);
