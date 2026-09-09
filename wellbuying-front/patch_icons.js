const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, 'src/app/login/page.tsx');
let content = fs.readFileSync(file, 'utf8');

const kakaoSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" className="h-6 w-6" fill="#000000"><path d="M12 3c-5.5 0-10 3.5-10 7.8 0 2.8 1.8 5.3 4.5 6.7-.2.8-.7 2.6-.8 2.8-.1.3.1.4.3.3.3-.2 3.1-2 4.4-2.9.5.1 1.1.2 1.6.2 5.5 0 10-3.5 10-7.8S17.5 3 12 3z"/></svg>`;

const googleSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" className="h-6 w-6"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.7 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.2-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/><path fill="none" d="M0 0h48v48H0z"/></svg>`;

const oldButtonContent = `{provider === "KAKAO" ? "K" : "G"}`;
const newButtonContent = `{provider === "KAKAO" ? (${kakaoSvg}) : (${googleSvg})}`;

content = content.replace(oldButtonContent, newButtonContent);

fs.writeFileSync(file, content);
