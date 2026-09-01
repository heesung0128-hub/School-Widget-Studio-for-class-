import { WidgetConfig } from '../types';

// Kotlin(MainActivity.decodeBase64UrlToString)과 짝을 이루는 인코더.
// UTF-8 바이트로 변환한 뒤 base64로 묶고, URL에 그대로 넣을 수 있도록
// URL-safe 문자로 치환하고 패딩(=)을 제거한다 (Kotlin 쪽에서 다시 채워 넣음).
function utf8ToBase64Url(str: string): string {
  const bytes = new TextEncoder().encode(str);
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  const base64 = btoa(binary);
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * 안드로이드 앱(android/app/.../MainActivity.kt)이 처리하는 딥링크를 만든다.
 * schoolwidget://import?config=<base64url(JSON)>
 * JSON: { school: {officeCode, schoolCode, schoolName}, ddays, timetable }
 */
export function buildImportDeepLink(config: WidgetConfig): string {
  const payload = {
    school: {
      officeCode: config.school.officeCode,
      schoolCode: config.school.schoolCode,
      schoolName: config.school.schoolName,
    },
    ddays: config.ddays.map((d) => ({ title: d.title, targetDate: d.targetDate })),
    timetable: config.timetable.map((t) => ({ day: t.day, periods: t.periods })),
    todos: config.todos.map((t) => ({ id: t.id, text: t.text, completed: t.completed })),
    showCalories: config.showCalories,
    mealSwitchTime: config.mealSwitchTime,
  };

  const encoded = utf8ToBase64Url(JSON.stringify(payload));
  return `schoolwidget://import?config=${encoded}`;
}
