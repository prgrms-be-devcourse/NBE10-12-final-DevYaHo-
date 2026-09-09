const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, 'src/components/account/ProfileContent.tsx');
let content = fs.readFileSync(file, 'utf8');

// 1. Remove the button from ProfileContent dl section
const oldBtnCode = `</dl>

        <div className="flex justify-end">
          <Button variant="secondary" className="px-3 py-1.5 text-xs text-wb-secondary" onClick={() => setPasswordResetOpen(true)}>비밀번호 재설정</Button>
        </div>`;

content = content.replace(oldBtnCode, '</dl>');

// 2. Add onRequestPasswordReset to EditProfileModal
content = content.replace(
  '  initialProfileImageUrl: string;\n  onSaved: (member: MemberResponse) => void;\n}) {',
  '  initialProfileImageUrl: string;\n  onSaved: (member: MemberResponse) => void;\n  onRequestPasswordReset: () => void;\n}) {'
);

// 3. Add the button inside EditProfileModal
const editModalButtonsOld = `        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" loading={submitting} disabled={name.length === 0}>
            저장
          </Button>
        </div>`;

const editModalButtonsNew = `        <div className="flex items-center justify-between border-t border-wb-line pt-4 mt-2">
          <Button type="button" variant="secondary" className="px-3 py-1.5 text-xs text-wb-secondary" onClick={onRequestPasswordReset}>
            비밀번호 재설정
          </Button>
          <div className="flex gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              취소
            </Button>
            <Button type="submit" loading={submitting} disabled={name.length === 0}>
              저장
            </Button>
          </div>
        </div>`;

content = content.replace(editModalButtonsOld, editModalButtonsNew);

// 4. Update the EditProfileModal invocation in ProfileContent
const editModalCallOld = `<EditProfileModal
          onClose={() => setEditOpen(false)}
          initialName={member.name}
          initialProfileImageUrl={member.profileImageUrl ?? ""}
          onSaved={setMember}
        />`;

const editModalCallNew = `<EditProfileModal
          onClose={() => setEditOpen(false)}
          initialName={member.name}
          initialProfileImageUrl={member.profileImageUrl ?? ""}
          onSaved={setMember}
          onRequestPasswordReset={() => {
            setEditOpen(false);
            setPasswordResetOpen(true);
          }}
        />`;

content = content.replace(editModalCallOld, editModalCallNew);

fs.writeFileSync(file, content);
