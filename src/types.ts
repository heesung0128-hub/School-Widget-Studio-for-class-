export interface DDayItem {
  id: string;
  title: string;
  targetDate: string; // YYYY-MM-DD
  color?: string;
}

export interface TimetableDay {
  day: '월' | '화' | '수' | '목' | '금';
  periods: string[];
}

export interface PeriodTime {
  period: number;
  startTime: string; // "09:00"
  endTime: string;   // "09:50"
}

export interface TodoItem {
  id: string;
  text: string;
  completed: boolean;
  createdAt: number;
}

export interface SchoolInfo {
  officeCode: string; // ATPT_OFCDC_SC_CODE (예: B10 - 서울특별시교육청)
  officeName: string;
  schoolCode: string; // SD_SCHUL_CODE
  schoolName: string;
  location: string;
}

export type WidgetTheme = 'dark-acrylic' | 'light-acrylic' | 'slate-glass' | 'emerald-glass' | 'indigo-glass' | 'sakura-glass';

export interface WidgetConfig {
  school: SchoolInfo;
  ddays: DDayItem[];
  timetable: TimetableDay[];
  periodTimes: PeriodTime[];
  todos: TodoItem[];
  theme: WidgetTheme;
  opacity: number; // 0.6 ~ 1.0
  alwaysOnTop: boolean;
  snapSide: 'left' | 'right'; // 화면 어느 쪽 상단에 자동으로 붙을지
  snapMargin: number; // px from top and the chosen side
  mealSwitchTime: string; // "13:30"
  showAllergies: boolean;
  showCalories: boolean;
  widgetWidth: number; // default 320px
  userRole: 'teacher' | 'student';
  gradeClass?: string; // e.g. "3-2"
  fontScale: number; // 0.85 ~ 1.3, 1.0 = 기본 글씨 크기
}

export interface MealData {
  date: string; // YYYYMMDD
  dateFormatted: string;
  mealType: string; // 조식, 중식, 석식
  menu: string[];
  calories: string;
  originInfo: string[];
  isNextDay: boolean;
  isWeekend?: boolean;
}
