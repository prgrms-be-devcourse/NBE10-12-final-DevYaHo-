const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, 'src/components/account/ProfileContent.tsx');
let content = fs.readFileSync(file, 'utf8');

// 1. Add imports for password reissue APIs
content = content.replace(
  'requestProfileImageUploadUrl,',
  'requestProfileImageUploadUrl,\n  sendPasswordReissueCode,\n  verifyPasswordReissueCode,\n  resetPassword,'
);

// 2. Insert PasswordResetModal component before ProfileContent
const modalCode = `
function PasswordResetModal({ email, onClose }: { email: string; onClose: () => void }) {
  const router = useRouter();
  const { setMember } = useAuth();
  
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  async function handleSend() {
    setLoading(true); setError(null); setSuccessMsg(null);
    try {
      await sendPasswordReissueCode(email);
      setSuccessMsg("이메일로 인증 코드가 발송되었습니다.");
      setStep(2);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "이메일 발송에 실패했어요.");
    } finally {
      setLoading(false);
    }
  }

  async function handleVerify(e: FormEvent) {
    e.preventDefault();
    setLoading(true); setError(null); setSuccessMsg(null);
    try {
      await verifyPasswordReissueCode(email, code);
      setSuccessMsg("인증이 완료되었습니다. 새 비밀번호를 입력해주세요.");
      setStep(3);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "인증에 실패했어요.");
    } finally {
      setLoading(false);
    }
  }

  async function handleReset(e: FormEvent) {
    e.preventDefault();
    setLoading(true); setError(null); setSuccessMsg(null);
    try {
      await resetPassword(email, newPassword);
      alert("비밀번호가 성공적으로 재설정되었습니다. 다시 로그인해주세요.");
      clearTokens();
      setMember(null);
      router.push("/login");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "비밀번호 재설정에 실패했어요.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal open onClose={onClose} title="비밀번호 재설정" width="380px">
      <div className="space-y-4">
        {step === 1 && (
          <div className="space-y-4">
            <p className="text-sm text-wb-secondary">
              현재 로그인된 계정({email})으로 비밀번호 재설정 코드를 발송합니다.
            </p>
            {successMsg && <Banner tone="success">{successMsg}</Banner>}
            {error && <Banner tone="error">{error}</Banner>}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={onClose}>취소</Button>
              <Button type="button" onClick={handleSend} loading={loading}>인증 코드 발송</Button>
            </div>
          </div>
        )}
        
        {step === 2 && (
          <form onSubmit={handleVerify} className="space-y-4">
            <p className="text-sm text-wb-secondary">
              이메일로 발송된 6자리 인증 코드를 입력해주세요.
            </p>
            <TextField label="인증 코드" value={code} onChange={(e) => setCode(e.target.value)} required />
            {successMsg && <Banner tone="success">{successMsg}</Banner>}
            {error && <Banner tone="error">{error}</Banner>}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={onClose}>취소</Button>
              <Button type="submit" loading={loading} disabled={code.length === 0}>코드 인증</Button>
            </div>
          </form>
        )}
        
        {step === 3 && (
          <form onSubmit={handleReset} className="space-y-4">
            <p className="text-sm text-wb-secondary">새롭게 사용할 비밀번호를 입력해주세요.</p>
            <TextField label="새 비밀번호" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required />
            {error && <Banner tone="error">{error}</Banner>}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={onClose}>취소</Button>
              <Button type="submit" loading={loading} disabled={newPassword.length < 8}>재설정 완료</Button>
            </div>
          </form>
        )}
      </div>
    </Modal>
  );
}

// showHeader=false:`;

content = content.replace('// showHeader=false:', modalCode);

// 3. Add password reset button and state
content = content.replace('const [withdrawOpen, setWithdrawOpen] = useState(false);', 'const [withdrawOpen, setWithdrawOpen] = useState(false);\n  const [passwordResetOpen, setPasswordResetOpen] = useState(false);');

const passwordBtnCode = `</dl>

        <div className="flex justify-end">
          <Button variant="secondary" className="px-3 py-1.5 text-xs text-wb-secondary" onClick={() => setPasswordResetOpen(true)}>비밀번호 재설정</Button>
        </div>`;

content = content.replace('</dl>', passwordBtnCode);

// 4. Add the modal renderer
const modalRendererCode = `
      {passwordResetOpen && (
        <PasswordResetModal email={member.email} onClose={() => setPasswordResetOpen(false)} />
      )}`;

content = content.replace('      {editOpen && (', modalRendererCode + '\n      {editOpen && (');

fs.writeFileSync(file, content);
