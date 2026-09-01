import React, { useState, useRef, useEffect } from 'react';
import {
  Calendar,
  Clock,
  Utensils,
  CheckSquare,
  Plus,
  Trash2,
  RotateCw,
  X,
  Sparkles,
  BookOpen,
  ChevronRight,
  Sun,
  Moon,
  Move,
  GripVertical,
  Monitor,
  Layers,
  HelpCircle,
  Maximize2,
  Sliders,
  Terminal,
  RefreshCw,
  LayoutGrid
} from 'lucide-react';
import { WidgetConfig, MealData, TodoItem } from '../types';
import { fetchNeisMeal, getMealTargetDate, formatDateToYMD } from '../utils/neisApi';

interface SchoolWidgetCardProps {
  config: WidgetConfig;
  onUpdateConfig?: (newConfig: WidgetConfig) => void;
  isDraggable?: boolean;
  onDragStart?: () => void;
  onDragEnd?: () => void;
}

export const SchoolWidgetCard: React.FC<SchoolWidgetCardProps> = ({
  config,
  onUpdateConfig,
}) => {
  const [currentTime, setCurrentTime] = useState<Date>(new Date());
  const [mealData, setMealData] = useState<MealData | null>(null);
  const [mealLoading, setMealLoading] = useState<boolean>(false);
  const [newTodoText, setNewTodoText] = useState<string>('');
  const [activeTab, setActiveTab] = useState<'today' | 'week'>('today');
  const [selectedDay, setSelectedDay] = useState<'월' | '화' | '수' | '목' | '금'>('월');
  const [draggedTodoId, setDraggedTodoId] = useState<string | null>(null);

  // Update clock every second
  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(new Date());
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  // Which meal date the widget should currently be showing (flips exactly at mealSwitchTime,
  // same as the generated PowerShell widget). Used as an effect dependency so the preview
  // re-fetches the instant the 13:30 boundary is crossed, not just on mount/config change.
  const { targetDate: mealTargetDate } = getMealTargetDate(currentTime, config.mealSwitchTime);
  const mealDateKey = formatDateToYMD(mealTargetDate);

  // Fetch meal data on school/time change, and whenever the target meal date flips
  useEffect(() => {
    let isMounted = true;
    setMealLoading(true);
    fetchNeisMeal(config.school, currentTime, config.mealSwitchTime, config.showAllergies)
      .then((data) => {
        if (isMounted) {
          setMealData(data);
          setMealLoading(false);
        }
      })
      .catch(() => {
        if (isMounted) setMealLoading(false);
      });
    return () => {
      isMounted = false;
    };
  }, [config.school, config.mealSwitchTime, config.showAllergies, mealDateKey]);

  // Current day of week in Korean
  const daysKor: ('일' | '월' | '화' | '수' | '목' | '금' | '토')[] = ['일', '월', '화', '수', '목', '금', '토'];
  const todayKor = daysKor[currentTime.getDay()];
  const currentTargetDay = ['월', '화', '수', '목', '금'].includes(todayKor) ? (todayKor as '월' | '화' | '수' | '목' | '금') : '월';

  // Set default selected day
  useEffect(() => {
    setSelectedDay(currentTargetDay);
  }, [currentTargetDay]);

  // Todo handlers
  const handleAddTodo = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!newTodoText.trim() || !onUpdateConfig) return;
    const newTodo: TodoItem = {
      id: Date.now().toString(),
      text: newTodoText.trim(),
      completed: false,
      createdAt: Date.now(),
    };
    onUpdateConfig({
      ...config,
      todos: [...config.todos, newTodo],
    });
    setNewTodoText('');
  };

  const handleToggleTodo = (id: string) => {
    if (!onUpdateConfig) return;
    onUpdateConfig({
      ...config,
      todos: config.todos.map((t) => (t.id === id ? { ...t, completed: !t.completed } : t)),
    });
  };

  const handleDeleteTodo = (id: string) => {
    if (!onUpdateConfig) return;
    onUpdateConfig({
      ...config,
      todos: config.todos.filter((t) => t.id !== id),
    });
  };

  // Drag-to-reorder todos
  const handleTodoDragStart = (id: string) => (e: React.DragEvent) => {
    setDraggedTodoId(id);
    e.dataTransfer.effectAllowed = 'move';
  };

  const handleTodoDragOver = (id: string) => (e: React.DragEvent) => {
    e.preventDefault();
    if (!onUpdateConfig || !draggedTodoId || draggedTodoId === id) return;
    const fromIndex = config.todos.findIndex((t) => t.id === draggedTodoId);
    const toIndex = config.todos.findIndex((t) => t.id === id);
    if (fromIndex === -1 || toIndex === -1) return;
    const reordered = [...config.todos];
    const [moved] = reordered.splice(fromIndex, 1);
    reordered.splice(toIndex, 0, moved);
    onUpdateConfig({ ...config, todos: reordered });
  };

  const handleTodoDragEnd = () => setDraggedTodoId(null);

  // Calculate D-Days
  const getDDayCalc = (targetDateStr: string) => {
    const today = new Date(currentTime.getFullYear(), currentTime.getMonth(), currentTime.getDate());
    const [y, m, d] = targetDateStr.split('-').map(Number);
    const target = new Date(y, m - 1, d);
    const diffTime = target.getTime() - today.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays === 0) return { text: 'D-DAY', type: 'urgent' };
    if (diffDays > 0) return { text: `D-${diffDays}`, type: diffDays <= 7 ? 'warning' : 'normal' };
    return { text: `D+${Math.abs(diffDays)}`, type: 'passed' };
  };

  // Theme styles
  const themeClasses: Record<string, {
    container: string;
    card: string;
    border: string;
    text: string;
    subText: string;
    accent: string;
    accentBg: string;
  }> = {
    'dark-acrylic': {
      container: 'bg-slate-900/90 text-slate-100 backdrop-blur-xl border-slate-700/60 shadow-2xl shadow-black/60',
      card: 'bg-slate-800/70 border-slate-700/60',
      border: 'border-slate-700/50',
      text: 'text-slate-100',
      subText: 'text-slate-400',
      accent: 'text-blue-400',
      accentBg: 'bg-blue-600 hover:bg-blue-500 text-white',
    },
    'light-acrylic': {
      container: 'bg-white/95 text-slate-800 backdrop-blur-xl border-slate-200/80 shadow-2xl shadow-slate-900/20',
      card: 'bg-slate-50/90 border-slate-200',
      border: 'border-slate-200',
      text: 'text-slate-800',
      subText: 'text-slate-500',
      accent: 'text-blue-600',
      accentBg: 'bg-blue-600 hover:bg-blue-700 text-white',
    },
    'slate-glass': {
      container: 'bg-slate-700/85 text-slate-50 backdrop-blur-xl border-slate-500/50 shadow-2xl shadow-black/60',
      card: 'bg-slate-600/60 border-slate-500/50',
      border: 'border-slate-500/40',
      text: 'text-slate-50',
      subText: 'text-slate-300',
      accent: 'text-sky-400',
      accentBg: 'bg-sky-600 hover:bg-sky-500 text-white',
    },
    'sakura-glass': {
      container: 'bg-pink-950/90 text-pink-50 backdrop-blur-xl border-pink-700/50 shadow-2xl shadow-black/60',
      card: 'bg-pink-900/60 border-pink-700/50',
      border: 'border-pink-700/40',
      text: 'text-pink-50',
      subText: 'text-pink-300',
      accent: 'text-pink-400',
      accentBg: 'bg-pink-600 hover:bg-pink-500 text-white',
    },
    'emerald-glass': {
      container: 'bg-emerald-950/90 text-emerald-50 backdrop-blur-xl border-emerald-700/50 shadow-2xl shadow-black/60',
      card: 'bg-emerald-900/60 border-emerald-700/50',
      border: 'border-emerald-700/40',
      text: 'text-emerald-50',
      subText: 'text-emerald-300/80',
      accent: 'text-emerald-400',
      accentBg: 'bg-emerald-600 hover:bg-emerald-500 text-white',
    },
    'indigo-glass': {
      container: 'bg-indigo-950/90 text-indigo-50 backdrop-blur-xl border-indigo-700/50 shadow-2xl shadow-black/60',
      card: 'bg-indigo-900/60 border-indigo-700/50',
      border: 'border-indigo-700/40',
      text: 'text-indigo-50',
      subText: 'text-indigo-300/80',
      accent: 'text-indigo-400',
      accentBg: 'bg-indigo-600 hover:bg-indigo-500 text-white',
    },
  };

  const currentTheme = themeClasses[config.theme] || themeClasses['dark-acrylic'];

  const currentTimetableDay = config.timetable.find((t) => t.day === selectedDay) || {
    day: selectedDay,
    periods: ['국어', '수학', '영어', '사회', '과학', '체육', '동아리'],
  };

  const pendingTodosCount = config.todos.filter((t) => !t.completed).length;

  return (
    <div
      id="school-widget-main-container"
      className={`relative rounded-2xl border transition-all duration-300 overflow-hidden select-none ${currentTheme.container}`}
      style={{
        width: `${config.widgetWidth || 330}px`,
        opacity: config.opacity || 0.95,
        zoom: config.fontScale || 1,
      }}
    >
      {/* Top Header / Drag Bar */}
      <div
        id="widget-drag-header"
        className="px-4 pt-3.5 pb-2.5 flex items-start justify-between cursor-move border-b border-white/5"
      >
        <div>
          <div className="flex items-center gap-1.5">
            <span className="text-[10px] font-bold tracking-wider uppercase px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-400 border border-blue-400/30">
              {config.school.schoolName || '학교 생활 위젯'}
            </span>
            <span className="text-[10px] text-slate-400">
              {config.userRole === 'teacher' ? '교사용' : '학생용'}
            </span>
          </div>
          <div className="mt-1 flex items-baseline gap-2">
            <h2 className="text-base font-bold tracking-tight">
              {currentTime.toLocaleDateString('ko-KR', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
                weekday: 'short',
              })}
            </h2>
            <span className={`text-xs font-medium font-mono ${currentTheme.subText}`}>
              {currentTime.toLocaleTimeString('ko-KR', {
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
              })}
            </span>
          </div>
        </div>

        <div className="flex items-center gap-1 text-slate-400">
          <div
            title={`마우스로 잡고 이동 (손을 떼면 ${config.snapSide === 'left' ? '좌측' : '우측'} 상단 자동 고정)`}
            className="p-1 rounded hover:bg-white/10 hover:text-white transition-colors"
          >
            <Move className="w-3.5 h-3.5" />
          </div>
        </div>
      </div>

      {/* Main Body */}
      <div className="p-3.5 space-y-3">
        {/* D-Day Badges */}
        {config.ddays && config.ddays.length > 0 && (
          <div id="widget-ddays-section" className="flex flex-wrap gap-1.5">
            {config.ddays.map((item) => {
              const ddayInfo = getDDayCalc(item.targetDate);
              let badgeColor = 'bg-blue-500/20 text-blue-300 border-blue-500/40';
              if (ddayInfo.type === 'urgent') {
                badgeColor = 'bg-red-500/20 text-red-300 border-red-500/50 animate-pulse';
              } else if (ddayInfo.type === 'warning') {
                badgeColor = 'bg-amber-500/20 text-amber-300 border-amber-500/40';
              } else if (ddayInfo.type === 'passed') {
                badgeColor = 'bg-slate-700/40 text-slate-400 border-slate-600/40';
              }

              return (
                <div
                  key={item.id}
                  className={`flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] border ${badgeColor}`}
                >
                  <span className="font-medium opacity-90">{item.title}</span>
                  <span className="font-bold tracking-tight">{ddayInfo.text}</span>
                </div>
              );
            })}
          </div>
        )}

        {/* Timetable Section (1-4교시 / 5-7교시 2열 배치) */}
        <div
          id="widget-timetable-section"
          className={`rounded-xl p-2.5 border transition-all ${currentTheme.card}`}
        >
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-1.5">
              <Clock className="w-3.5 h-3.5 text-blue-400" />
              <span className="text-xs font-bold">시간표</span>
            </div>

            {/* Day selector pills */}
            <div className="flex gap-0.5 bg-black/20 p-0.5 rounded-lg border border-white/5">
              {(['월', '화', '수', '목', '금'] as const).map((day) => (
                <button
                  key={day}
                  type="button"
                  onClick={() => setSelectedDay(day)}
                  className={`text-[10px] px-1.5 py-0.5 rounded-md font-semibold transition-all ${
                    selectedDay === day
                      ? `${currentTheme.accentBg} shadow-xs`
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  {day}
                </button>
              ))}
            </div>
          </div>

          {/* 2-Column Timetable: 1-4교시 (좌) & 5-7교시 (우) */}
          <div className="grid grid-cols-2 gap-2 text-[11px]">
            {/* 1열: 1-4교시 */}
            <div className="space-y-1">
              <div className="text-[10px] font-bold text-slate-400 px-0.5 flex items-center justify-between">
                <span>오전 (1~4교시)</span>
              </div>
              {currentTimetableDay.periods.slice(0, 4).map((subject, idx) => {
                const isToday = selectedDay === currentTargetDay;
                return (
                  <div
                    key={idx}
                    className={`px-2 py-1 rounded-lg border flex items-center justify-between gap-1.5 min-h-[26px] ${
                      isToday
                        ? 'bg-blue-500/10 border-blue-500/25 text-slate-100'
                        : 'bg-white/5 border-white/5 text-slate-200'
                    }`}
                  >
                    <span className="text-[10px] font-mono text-blue-400 font-bold shrink-0">{idx + 1}교시</span>
                    <span className="font-semibold text-[11px] truncate text-right flex-1" title={subject}>
                      {subject || '-'}
                    </span>
                  </div>
                );
              })}
            </div>

            {/* 2열: 5-7교시 */}
            <div className="space-y-1">
              <div className="text-[10px] font-bold text-slate-400 px-0.5 flex items-center justify-between">
                <span>오후 (5~7교시)</span>
              </div>
              {currentTimetableDay.periods.slice(4, 7).map((subject, idx) => {
                const isToday = selectedDay === currentTargetDay;
                return (
                  <div
                    key={idx + 4}
                    className={`px-2 py-1 rounded-lg border flex items-center justify-between gap-1.5 min-h-[26px] ${
                      isToday
                        ? 'bg-emerald-500/10 border-emerald-500/25 text-slate-100'
                        : 'bg-white/5 border-white/5 text-slate-200'
                    }`}
                  >
                    <span className="text-[10px] font-mono text-emerald-400 font-bold shrink-0">{idx + 5}교시</span>
                    <span className="font-semibold text-[11px] truncate text-right flex-1" title={subject}>
                      {subject || '-'}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* NEIS Meal Section (스크롤 없이 2열 그리드로 모든 메뉴 표시) */}
        <div
          id="widget-meal-section"
          className={`rounded-xl p-2.5 border transition-all ${currentTheme.card}`}
        >
          <div className="flex items-center justify-between mb-1.5">
            <div className="flex items-center gap-1.5">
              <Utensils className="w-3.5 h-3.5 text-emerald-400" />
              <span className="text-xs font-bold">
                {mealData?.isNextDay ? '내일의 급식' : '오늘의 급식'}
              </span>
              <span className="text-[10px] px-1.5 py-0.2 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                {mealData?.dateFormatted || '급식 정보'}
              </span>
            </div>

            <span className="text-[9px] text-slate-400 font-medium">
              {config.mealSwitchTime} 전환
            </span>
          </div>

          {mealLoading ? (
            <div className="py-3 flex items-center justify-center gap-2 text-xs text-slate-400">
              <RotateCw className="w-3.5 h-3.5 animate-spin text-blue-400" />
              <span>나이스 급식 정보를 조회 중...</span>
            </div>
          ) : (
            <div className="space-y-1.5">
              <div className="text-[11px] text-slate-200 bg-black/20 p-2 rounded-lg border border-white/5">
                {mealData?.menu && mealData.menu.length > 0 ? (
                  <div className="grid grid-cols-2 gap-x-2 gap-y-1">
                    {mealData.menu.map((dish, i) => (
                      <div key={i} className="flex items-start gap-1 text-[11px] leading-tight">
                        <span className="text-emerald-400 text-[10px] shrink-0 mt-0.5">•</span>
                        <span className="break-words font-medium">{dish}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <span className="text-slate-400 text-xs">등록된 급식 정보가 없습니다. (휴일 또는 미등록)</span>
                )}
              </div>

              {config.showCalories && mealData?.calories && (
                <div className="flex items-center justify-between text-[10px] text-slate-400 px-1">
                  <span>열량: {mealData.calories}</span>
                  <span className="text-[9px] text-slate-500">나이스 Open API 연동</span>
                </div>
              )}
            </div>
          )}
        </div>

        {/* TO-DO List Section (Dynamic Height) */}
        <div
          id="widget-todo-section"
          className={`rounded-xl p-2.5 border transition-all ${currentTheme.card}`}
        >
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-1.5">
              <CheckSquare className="w-3.5 h-3.5 text-amber-400" />
              <span className="text-xs font-bold">할 일 목록 (TO-DO)</span>
            </div>
            <span className="text-[10px] font-medium text-slate-400">
              {pendingTodosCount}개 남음
            </span>
          </div>

          {/* Add Todo Input */}
          <form onSubmit={handleAddTodo} className="flex gap-1.5 mb-2">
            <input
              type="text"
              value={newTodoText}
              onChange={(e) => setNewTodoText(e.target.value)}
              placeholder="새로운 할 일 입력 (Enter)..."
              className="flex-1 px-2.5 py-1 text-xs rounded-lg bg-black/25 border border-white/10 text-white placeholder:text-slate-500 focus:outline-none focus:border-blue-400"
            />
            <button
              type="submit"
              className={`px-2.5 py-1 rounded-lg text-xs font-bold transition-colors ${currentTheme.accentBg}`}
            >
              추가
            </button>
          </form>

          {/* Todo List */}
          <div className="space-y-1 max-h-48 overflow-y-auto pr-0.5">
            {config.todos.length === 0 ? (
              <p className="text-[11px] text-center py-2 text-slate-400">
                오늘 완료할 일을 추가해 보세요!
              </p>
            ) : (
              config.todos.map((todo) => (
                <div
                  key={todo.id}
                  draggable={!!onUpdateConfig}
                  onDragStart={handleTodoDragStart(todo.id)}
                  onDragOver={handleTodoDragOver(todo.id)}
                  onDragEnd={handleTodoDragEnd}
                  className={`group flex items-center justify-between p-1.5 rounded-lg border transition-all ${
                    draggedTodoId === todo.id ? 'opacity-40' : ''
                  } ${
                    todo.completed
                      ? 'bg-black/10 border-white/5 text-slate-400 opacity-60 line-through'
                      : 'bg-white/5 border-white/10 text-slate-200'
                  }`}
                >
                  <div className="flex items-center gap-1 flex-1 min-w-0">
                    <GripVertical className="w-3.5 h-3.5 text-slate-500 shrink-0 cursor-grab active:cursor-grabbing opacity-0 group-hover:opacity-100 transition-opacity" />
                    <label className="flex items-center gap-2 flex-1 cursor-pointer truncate">
                      <input
                        type="checkbox"
                        checked={todo.completed}
                        onChange={() => handleToggleTodo(todo.id)}
                        className="w-3.5 h-3.5 rounded bg-slate-800 border-slate-600 text-blue-500 focus:ring-0 cursor-pointer"
                      />
                      <span className="text-[11px] truncate select-text">{todo.text}</span>
                    </label>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleDeleteTodo(todo.id)}
                    className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-red-400 transition-opacity"
                    title="삭제"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

interface DesktopSimulatorProps {
  config: WidgetConfig;
  onUpdateConfig: (newConfig: WidgetConfig) => void;
}

export const DesktopSimulator: React.FC<DesktopSimulatorProps> = ({
  config,
  onUpdateConfig,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [dragOffset, setDragOffset] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [wallpaper, setWallpaper] = useState<'bloom' | 'nature' | 'minimal' | 'dark'>('bloom');
  const [dualMonitor, setDualMonitor] = useState<boolean>(false);
  const [activeMonitor, setActiveMonitor] = useState<1 | 2>(1);
  const [snappedMessage, setSnappedMessage] = useState<string | null>(null);

  // Initialize position to top-left or top-right on mount or container resize,
  // depending on config.snapSide
  const snapToTopRight = (monitorIndex: 1 | 2 = activeMonitor) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const margin = config.snapMargin || 20;
    const widgetWidth = config.widgetWidth || 330;
    const isLeft = config.snapSide === 'left';

    let targetX = isLeft ? margin : rect.width - widgetWidth - margin;
    if (dualMonitor) {
      const halfWidth = rect.width / 2;
      if (monitorIndex === 1) {
        // First monitor (left half)
        targetX = isLeft ? margin : halfWidth - widgetWidth - margin;
      } else {
        // Second monitor (right half)
        targetX = isLeft ? halfWidth + margin : rect.width - widgetWidth - margin;
      }
    }

    setPosition({
      x: Math.max(margin, targetX),
      y: margin,
    });

    setSnappedMessage(`모니터 ${monitorIndex} ${isLeft ? '좌측' : '우측'} 상단으로 자동 스냅되었습니다!`);
    setTimeout(() => setSnappedMessage(null), 2500);
  };

  useEffect(() => {
    snapToTopRight(activeMonitor);
  }, [dualMonitor, config.snapMargin, config.snapSide, config.widgetWidth]);

  // Drag handlers
  const handleMouseDown = (e: React.MouseEvent) => {
    // Only allow drag when clicking header or drag handle
    const target = e.target as HTMLElement;
    if (target.closest('button') || target.closest('input') || target.closest('form')) {
      return;
    }

    setIsDragging(true);
    if (containerRef.current) {
      const containerRect = containerRef.current.getBoundingClientRect();
      setDragOffset({
        x: e.clientX - containerRect.left - position.x,
        y: e.clientY - containerRect.top - position.y,
      });
    }
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging || !containerRef.current) return;
    const containerRect = containerRef.current.getBoundingClientRect();
    const newX = e.clientX - containerRect.left - dragOffset.x;
    const newY = e.clientY - containerRect.top - dragOffset.y;

    setPosition({
      x: Math.max(10, Math.min(containerRect.width - (config.widgetWidth || 330) - 10, newX)),
      y: Math.max(10, Math.min(containerRect.height - 150, newY)),
    });
  };

  const handleMouseUp = () => {
    if (!isDragging || !containerRef.current) return;
    setIsDragging(false);

    // Determine which monitor the widget was dropped in
    const containerRect = containerRef.current.getBoundingClientRect();
    let targetMon: 1 | 2 = 1;
    if (dualMonitor) {
      const midPoint = containerRect.width / 2;
      targetMon = position.x > midPoint ? 2 : 1;
      setActiveMonitor(targetMon);
    }

    // Snap to top-right of that monitor
    snapToTopRight(targetMon);
  };

  // Wallpaper styles
  const wallpapers = {
    bloom: 'bg-gradient-to-br from-indigo-900 via-slate-900 to-sky-950',
    nature: 'bg-gradient-to-br from-emerald-950 via-teal-900 to-slate-900',
    minimal: 'bg-gradient-to-br from-slate-900 via-zinc-900 to-stone-900',
    dark: 'bg-gradient-to-br from-black via-slate-950 to-neutral-900',
  };

  return (
    <div className="flex flex-col h-full space-y-3">
      {/* Desktop Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-2 p-2.5 bg-slate-900/60 border border-slate-800 rounded-xl text-xs text-slate-300">
        <div className="flex items-center gap-2">
          <span className="font-bold flex items-center gap-1.5 text-slate-200">
            <Monitor className="w-4 h-4 text-blue-400" />
            윈도우 바탕화면 실시간 시뮬레이터
          </span>
          <span className="hidden sm:inline-block px-2 py-0.5 rounded-full bg-blue-500/10 text-blue-400 border border-blue-500/20 text-[11px]">
            드래그 후 손을 떼면 {config.snapSide === 'left' ? '좌측' : '우측'} 상단 자동 스냅
          </span>
        </div>

        <div className="flex items-center gap-2">
          {/* Dual monitor toggle */}
          <button
            type="button"
            onClick={() => setDualMonitor(!dualMonitor)}
            className={`px-2.5 py-1 rounded-lg border font-medium flex items-center gap-1.5 transition-colors ${
              dualMonitor
                ? 'bg-blue-600 border-blue-500 text-white'
                : 'bg-slate-800 border-slate-700 hover:bg-slate-700 text-slate-300'
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            <span>{dualMonitor ? '듀얼 모니터 (ON)' : '싱글 모니터'}</span>
          </button>

          {/* Reset position button */}
          <button
            type="button"
            onClick={() => snapToTopRight(activeMonitor)}
            className="px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700 hover:bg-slate-700 text-slate-300 flex items-center gap-1 transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>위치 재정렬</span>
          </button>
        </div>
      </div>

      {/* Interactive Mock Desktop Canvas */}
      <div
        ref={containerRef}
        id="windows-mock-desktop"
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        className={`relative w-full min-h-[680px] h-[680px] rounded-2xl border border-slate-700/60 overflow-hidden shadow-inner select-none ${wallpapers[wallpaper]}`}
      >
        {/* Subtle grid and decorative background elements */}
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_120%,rgba(120,119,198,0.15),rgba(255,255,255,0))]" />

        {/* Dual monitor divider indicator */}
        {dualMonitor && (
          <div className="absolute top-0 bottom-10 left-1/2 -translate-x-1/2 border-r-2 border-dashed border-white/20 flex flex-col items-center justify-start pt-3 z-0 pointer-events-none">
            <span className="px-2 py-0.5 rounded bg-black/60 text-[10px] text-slate-300 border border-white/10 backdrop-blur-sm">
              🖥️ 모니터 1 ┃ 🖥️ 모니터 2 (구분선)
            </span>
          </div>
        )}

        {/* Snapped Notification Toast */}
        {snappedMessage && (
          <div className="absolute top-4 left-1/2 -translate-x-1/2 z-30 px-3 py-1.5 rounded-full bg-blue-600/90 text-white text-xs font-semibold shadow-lg backdrop-blur-md flex items-center gap-1.5 animate-bounce">
            <Sparkles className="w-3.5 h-3.5" />
            {snappedMessage}
          </div>
        )}

        {/* Desktop Icons Placeholder (Adds Windows realism) */}
        <div className="absolute top-4 left-4 space-y-3 z-10 pointer-events-none">
          <div className="flex flex-col items-center w-16 p-1.5 rounded hover:bg-white/10 text-white/90 text-center">
            <div className="w-8 h-8 rounded-lg bg-blue-500/30 border border-blue-400/40 flex items-center justify-center text-sm shadow-sm mb-1">
              📂
            </div>
            <span className="text-[10px] drop-shadow-md font-medium">내 PC</span>
          </div>
          <div className="flex flex-col items-center w-16 p-1.5 rounded hover:bg-white/10 text-white/90 text-center">
            <div className="w-8 h-8 rounded-lg bg-emerald-500/30 border border-emerald-400/40 flex items-center justify-center text-sm shadow-sm mb-1">
              🗑️
            </div>
            <span className="text-[10px] drop-shadow-md font-medium">휴지통</span>
          </div>
          <div className="flex flex-col items-center w-16 p-1.5 rounded hover:bg-white/10 text-white/90 text-center">
            <div className="w-8 h-8 rounded-lg bg-amber-500/30 border border-amber-400/40 flex items-center justify-center text-sm shadow-sm mb-1">
              🏫
            </div>
            <span className="text-[10px] drop-shadow-md font-medium">나이스</span>
          </div>
        </div>

        {/* Draggable Widget Component */}
        <div
          id="draggable-school-widget"
          onMouseDown={handleMouseDown}
          className={`absolute z-20 transition-all ${
            isDragging ? 'cursor-grabbing scale-[1.01] shadow-2xl opacity-90' : 'transition-all duration-300 ease-out'
          }`}
          style={{
            transform: `translate3d(${position.x}px, ${position.y}px, 0)`,
          }}
        >
          <SchoolWidgetCard
            config={config}
            onUpdateConfig={onUpdateConfig}
            isDraggable
          />
        </div>

        {/* Windows 11 Taskbar Simulation */}
        <div className="absolute bottom-0 left-0 right-0 h-11 bg-slate-950/80 backdrop-blur-xl border-t border-slate-700/50 flex items-center justify-between px-3 z-30">
          {/* Windows Start and Center Icons */}
          <div className="flex items-center gap-1.5 mx-auto">
            <div className="w-7 h-7 rounded-md hover:bg-white/10 flex items-center justify-center text-blue-400 font-bold text-sm cursor-pointer transition-colors">
              🪟
            </div>
            <div className="w-7 h-7 rounded-md hover:bg-white/10 flex items-center justify-center text-slate-300 text-xs cursor-pointer transition-colors">
              🔍
            </div>
            <div className="w-7 h-7 rounded-md bg-blue-500/20 border border-blue-400/30 flex items-center justify-center text-blue-300 text-xs cursor-pointer">
              🏫
            </div>
            <div className="w-7 h-7 rounded-md hover:bg-white/10 flex items-center justify-center text-slate-300 text-xs cursor-pointer transition-colors">
              📁
            </div>
          </div>

          {/* System Tray (Clock & Wifi) */}
          <div className="flex items-center gap-2 text-slate-300 text-xs font-mono">
            <span className="text-[11px] text-slate-400">ENG</span>
            <div className="text-right leading-tight text-[10px]">
              <div>{new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}</div>
              <div className="text-slate-400">{new Date().toLocaleDateString('ko-KR', { month: '2-digit', day: '2-digit' })}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
