import React, { useState } from 'react';
import { 
  SchoolInfo, 
  WidgetConfig, 
  DDayItem, 
  TimetableDay, 
  WidgetTheme 
} from '../types';
import { searchSchools, DEFAULT_OFFICES } from '../utils/neisApi';
import { 
  Search, 
  School, 
  Calendar, 
  Clock, 
  Palette, 
  Plus, 
  Trash2, 
  Check, 
  Sparkles,
  Sliders,
  Utensils,
  BookOpen,
  ArrowUp,
  ArrowDown,
  GripVertical
} from 'lucide-react';

interface ConfigPanelProps {
  config: WidgetConfig;
  onUpdateConfig: (newConfig: WidgetConfig) => void;
  // Simulator 탭 사이드바에 끼워 넣는 미니 패널용 - true면 원래의 작은 글씨 크기를 쓰고,
  // false(기본값, "위젯 커스텀 설정" 탭 전용)면 잘 읽히도록 키운 글씨 크기를 씀
  compact?: boolean;
}

export const ConfigPanel: React.FC<ConfigPanelProps> = ({
  config,
  onUpdateConfig,
  compact = false,
}) => {
  // large: 위젯 커스텀 설정 탭에서 쓰는 확대 크기 / small: 시뮬레이터 미니 패널의 원래 크기
  const sz = (large: string, small: string) => (compact ? small : large);
  const [activeTab, setActiveTab] = useState<'school' | 'ddays' | 'timetable' | 'style'>('school');
  
  // School search state
  const [searchKeyword, setSearchKeyword] = useState<string>('');
  const [searchResults, setSearchResults] = useState<SchoolInfo[]>([]);
  const [isSearching, setIsSearching] = useState<boolean>(false);
  const [searchMessage, setSearchMessage] = useState<string>('');

  // New D-Day state
  const [newDDayTitle, setNewDDayTitle] = useState<string>('');
  const [newDDayDate, setNewDDayDate] = useState<string>('');

  // Handle school search
  const handleSearchSchool = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchKeyword.trim()) return;
    setIsSearching(true);
    setSearchMessage('');
    try {
      const results = await searchSchools(searchKeyword.trim());
      setSearchResults(results);
      if (results.length === 0) {
        setSearchMessage('일치하는 학교를 찾을 수 없습니다. 학교 이름을 다시 확인해 주세요.');
      }
    } catch (err) {
      setSearchMessage('학교 검색 중 오류가 발생했습니다.');
    } finally {
      setIsSearching(false);
    }
  };

  const handleSelectSchool = (sch: SchoolInfo) => {
    onUpdateConfig({
      ...config,
      school: sch,
    });
    setSearchResults([]);
    setSearchKeyword('');
  };

  // D-Day handlers
  const handleAddDDay = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newDDayTitle.trim() || !newDDayDate) return;
    const newItem: DDayItem = {
      id: Date.now().toString(),
      title: newDDayTitle.trim(),
      targetDate: newDDayDate,
    };
    onUpdateConfig({
      ...config,
      ddays: [...config.ddays, newItem],
    });
    setNewDDayTitle('');
    setNewDDayDate('');
  };

  const handleDeleteDDay = (id: string) => {
    onUpdateConfig({
      ...config,
      ddays: config.ddays.filter((d) => d.id !== id),
    });
  };

  const handleMoveDDay = (index: number, direction: 'up' | 'down') => {
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= config.ddays.length) return;

    const newDDays = [...config.ddays];
    const temp = newDDays[index];
    newDDays[index] = newDDays[targetIndex];
    newDDays[targetIndex] = temp;

    onUpdateConfig({
      ...config,
      ddays: newDDays,
    });
  };

  // Timetable update handler
  const handleTimetableChange = (day: '월' | '화' | '수' | '목' | '금', periodIndex: number, value: string) => {
    const updated = config.timetable.map((t) => {
      if (t.day === day) {
        const newPeriods = [...t.periods];
        newPeriods[periodIndex] = value;
        return { ...t, periods: newPeriods };
      }
      return t;
    });
    onUpdateConfig({
      ...config,
      timetable: updated,
    });
  };

  // Timetable Presets
  const applyTimetablePreset = (preset: 'high-teacher' | 'middle-teacher' | 'elem-student') => {
    let newTable: TimetableDay[] = [];
    if (preset === 'high-teacher') {
      newTable = [
        { day: '월', periods: ['문학 (3-1)', '문학 (3-2)', '상담', '수업준비', '진로지도', '동아리', '종례'] },
        { day: '화', periods: ['문학 (3-3)', '문학 (3-1)', '교직회의', '문학 (3-2)', '수업준비', '보충학습', '-'] },
        { day: '수', periods: ['수업준비', '문학 (3-3)', '문학 (3-1)', '전문학습', '자율학습', '-', '-'] },
        { day: '목', periods: ['문학 (3-2)', '문학 (3-3)', '문학 (3-1)', '학생상담', '수업준비', '진로활동', '-'] },
        { day: '금', periods: ['문학 (3-2)', '수업준비', '문학 (3-3)', '학년회의', '학급자치', '클럽활동', '-'] },
      ];
    } else if (preset === 'middle-teacher') {
      newTable = [
        { day: '월', periods: ['국어 (2-1)', '국어 (2-2)', '자유학기', '수업준비', '국어 (2-3)', '동아리', '-'] },
        { day: '화', periods: ['수업준비', '국어 (2-1)', '교직회의', '국어 (2-2)', '국어 (2-3)', '진로상담', '-'] },
        { day: '수', periods: ['국어 (2-2)', '국어 (2-3)', '전문적학습', '국어 (2-1)', '자율활동', '-', '-'] },
        { day: '목', periods: ['국어 (2-3)', '수업준비', '국어 (2-1)', '국어 (2-2)', '학생상담', '체육대회', '-'] },
        { day: '금', periods: ['국어 (2-1)', '국어 (2-2)', '수업준비', '국어 (2-3)', '학급회의', '창의체험', '-'] },
      ];
    } else {
      newTable = [
        { day: '월', periods: ['국어', '수학', '사회', '과학', '체육', '음악', '-'] },
        { day: '화', periods: ['수학', '영어', '국어', '도덕', '미술', '미술', '-'] },
        { day: '수', periods: ['과학', '국어', '체육', '영어', '수학', '-', '-'] },
        { day: '목', periods: ['사회', '수학', '국어', '실과', '실과', '체육', '-'] },
        { day: '금', periods: ['영어', '국어', '수학', '음악', '창체', '동아리', '-'] },
      ];
    }

    onUpdateConfig({
      ...config,
      timetable: newTable,
    });
  };

  return (
    <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-4 text-slate-200">
      {/* Tab Navigation */}
      <div className="flex border-b border-slate-800 pb-3 gap-1 overflow-x-auto">
        <button
          type="button"
          onClick={() => setActiveTab('school')}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg ${sz('text-base','text-xs')} font-semibold transition-colors shrink-0 ${
            activeTab === 'school'
              ? 'bg-blue-600 text-white'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'
          }`}
        >
          <School className="w-3.5 h-3.5" />
          <span>학교 & 급식 설정</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('ddays')}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg ${sz('text-base','text-xs')} font-semibold transition-colors shrink-0 ${
            activeTab === 'ddays'
              ? 'bg-blue-600 text-white'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'
          }`}
        >
          <Calendar className="w-3.5 h-3.5" />
          <span>D-Day 관리 ({config.ddays.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('timetable')}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg ${sz('text-base','text-xs')} font-semibold transition-colors shrink-0 ${
            activeTab === 'timetable'
              ? 'bg-blue-600 text-white'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'
          }`}
        >
          <Clock className="w-3.5 h-3.5" />
          <span>시간표 편집</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('style')}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg ${sz('text-base','text-xs')} font-semibold transition-colors shrink-0 ${
            activeTab === 'style'
              ? 'bg-blue-600 text-white'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'
          }`}
        >
          <Palette className="w-3.5 h-3.5" />
          <span>디자인 & 스냅</span>
        </button>
      </div>

      {/* Tab Contents */}
      <div className="pt-4">
        {/* 1. School & Meal Settings */}
        {activeTab === 'school' && (
          <div className="space-y-4">
            {/* Current Selected School */}
            <div className="p-3 bg-slate-800/60 rounded-xl border border-slate-700/70">
              <div className={`${sz('text-base','text-xs')} text-slate-400 mb-1`}>현재 적용된 학교</div>
              <div className="flex items-center justify-between">
                <div>
                  <h4 className={`${sz('text-lg','text-sm')} font-bold text-blue-400`}>{config.school.schoolName}</h4>
                  <p className={`${sz('text-base','text-xs')} text-slate-400 mt-0.5`}>{config.school.officeName} ({config.school.officeCode}) • 코드: {config.school.schoolCode}</p>
                </div>
                <span className={`px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 ${sz('text-[14px]','text-[11px]')}`}>
                  NEIS 연동 활성화
                </span>
              </div>
            </div>

            {/* NEIS School Search Input */}
            <form onSubmit={handleSearchSchool} className="space-y-2">
              <label className={`block ${sz('text-base','text-xs')} font-semibold text-slate-300`}>
                🔍 나이스(NEIS) 학교 검색
              </label>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  placeholder="예: 서울고등학교, 대전중학교..."
                  className={`flex-1 px-3 py-2 ${sz('text-base','text-xs')} rounded-xl bg-slate-800 border border-slate-700 text-white placeholder:text-slate-500 focus:outline-none focus:border-blue-500`}
                />
                <button
                  type="submit"
                  disabled={isSearching}
                  className={`px-4 py-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white ${sz('text-base','text-xs')} font-bold rounded-xl flex items-center gap-1.5 transition-colors`}
                >
                  <Search className="w-3.5 h-3.5" />
                  <span>{isSearching ? '검색 중...' : '검색'}</span>
                </button>
              </div>
            </form>

            {searchMessage && (
              <p className={`${sz('text-base','text-xs')} text-amber-400`}>{searchMessage}</p>
            )}

            {/* Search Results List */}
            {searchResults.length > 0 && (
              <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
                <div className={`${sz('text-base','text-xs')} text-slate-400`}>검색 결과 ({searchResults.length}건):</div>
                {searchResults.map((sch) => (
                  <button
                    key={sch.schoolCode}
                    type="button"
                    onClick={() => handleSelectSchool(sch)}
                    className="w-full text-left p-2.5 rounded-xl bg-slate-800/80 hover:bg-blue-900/40 border border-slate-700 hover:border-blue-500/50 transition-all flex items-center justify-between group"
                  >
                    <div>
                      <div className={`${sz('text-base','text-xs')} font-bold text-slate-200 group-hover:text-blue-300`}>
                        {sch.schoolName}
                      </div>
                      <div className={`${sz('text-[14px]','text-[11px]')} text-slate-400`}>
                        {sch.officeName} • {sch.location || '위치 정보 없음'}
                      </div>
                    </div>
                    <span className={`${sz('text-base','text-xs')} text-blue-400 group-hover:underline`}>선택하기</span>
                  </button>
                ))}
              </div>
            )}

            {/* Meal Time Switch & Options */}
            <div className="pt-2 border-t border-slate-800 space-y-3">
              <h4 className={`${sz('text-base','text-xs')} font-bold text-slate-300 flex items-center gap-1.5`}>
                <Utensils className="w-3.5 h-3.5 text-emerald-400" />
                급식 시간 자동 전환 규칙 (13:30 기준)
              </h4>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className={`block ${sz('text-[14px]','text-[11px]')} text-slate-400 mb-1`}>
                    내일 급식 전환 기준 시각
                  </label>
                  <input
                    type="time"
                    value={config.mealSwitchTime}
                    onChange={(e) => onUpdateConfig({ ...config, mealSwitchTime: e.target.value })}
                    className={`w-full px-3 py-1.5 ${sz('text-base','text-xs')} rounded-lg bg-slate-800 border border-slate-700 text-white focus:outline-none focus:border-blue-500`}
                  />
                  <span className={`${sz('text-[13px]','text-[10px]')} text-slate-400 mt-1 block`}>
                    설정한 시간 이후에는 자동으로 '내일의 급식'(금요일은 월요일)이 표시됩니다.
                  </span>
                </div>

                <div className="space-y-2">
                  <label className={`flex items-center gap-2 cursor-pointer ${sz('text-base','text-xs')} text-slate-300`}>
                    <input
                      type="checkbox"
                      checked={config.showAllergies}
                      onChange={(e) => onUpdateConfig({ ...config, showAllergies: e.target.checked })}
                      className="rounded bg-slate-800 border-slate-700 text-blue-600 focus:ring-0"
                    />
                    <span>알레르기 유발물질 번호 표시</span>
                  </label>

                  <label className={`flex items-center gap-2 cursor-pointer ${sz('text-base','text-xs')} text-slate-300`}>
                    <input
                      type="checkbox"
                      checked={config.showCalories}
                      onChange={(e) => onUpdateConfig({ ...config, showCalories: e.target.checked })}
                      className="rounded bg-slate-800 border-slate-700 text-blue-600 focus:ring-0"
                    />
                    <span>예상 열량(Kcal) 정보 표시</span>
                  </label>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* 2. D-Day Manager */}
        {activeTab === 'ddays' && (
          <div className="space-y-4">
            {/* Add D-Day form */}
            <form onSubmit={handleAddDDay} className="p-3 bg-slate-800/60 rounded-xl border border-slate-700/60 space-y-2">
              <div className={`${sz('text-base','text-xs')} font-bold text-slate-300`}>새 D-Day 추가</div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                <input
                  type="text"
                  value={newDDayTitle}
                  onChange={(e) => setNewDDayTitle(e.target.value)}
                  placeholder="예: 1학기 중간고사, 여름방학, 수능..."
                  className={`px-3 py-1.5 ${sz('text-base','text-xs')} rounded-lg bg-slate-800 border border-slate-700 text-white placeholder:text-slate-500 focus:outline-none focus:border-blue-500`}
                />
                <input
                  type="date"
                  value={newDDayDate}
                  onChange={(e) => setNewDDayDate(e.target.value)}
                  className={`px-3 py-1.5 ${sz('text-base','text-xs')} rounded-lg bg-slate-800 border border-slate-700 text-white focus:outline-none focus:border-blue-500`}
                />
              </div>
              <button
                type="submit"
                className={`w-full py-1.5 bg-blue-600 hover:bg-blue-500 text-white ${sz('text-base','text-xs')} font-bold rounded-lg flex items-center justify-center gap-1.5 transition-colors`}
              >
                <Plus className="w-3.5 h-3.5" />
                <span>D-Day 등록</span>
              </button>
            </form>

            {/* Existing D-Days */}
            <div className="space-y-1.5">
              <div className={`flex items-center justify-between ${sz('text-base','text-xs')} text-slate-400`}>
                <span>등록된 D-Day 목록 ({config.ddays.length}개):</span>
                <span className={`${sz('text-[14px]','text-[11px]')} text-slate-500`}>화살표(↑↓)로 위젯 표시 순서 변경</span>
              </div>
              {config.ddays.length === 0 ? (
                <p className={`${sz('text-base','text-xs')} text-slate-500 py-3 text-center`}>등록된 D-Day가 없습니다.</p>
              ) : (
                config.ddays.map((d, idx) => (
                  <div
                    key={d.id}
                    className="p-2.5 rounded-xl bg-slate-800 border border-slate-700 flex items-center justify-between gap-2 hover:border-slate-600 transition-colors"
                  >
                    <div className="flex items-center gap-2 flex-1 min-w-0">
                      <div className={`w-5 h-5 rounded-md bg-slate-700/60 flex items-center justify-center ${sz('text-[13px]','text-[10px]')} font-bold text-slate-400 shrink-0`}>
                        {idx + 1}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className={`${sz('text-base','text-xs')} font-bold text-slate-200 truncate`}>{d.title}</div>
                        <div className={`${sz('text-[14px]','text-[11px]')} text-slate-400`}>목표일: {d.targetDate}</div>
                      </div>
                    </div>

                    <div className="flex items-center gap-1 shrink-0">
                      {/* Reorder Up Button */}
                      <button
                        type="button"
                        onClick={() => handleMoveDDay(idx, 'up')}
                        disabled={idx === 0}
                        className="p-1.5 rounded-lg bg-slate-700/50 hover:bg-blue-600 hover:text-white text-slate-300 disabled:opacity-30 disabled:hover:bg-slate-700/50 disabled:hover:text-slate-300 transition-colors"
                        title={idx === 0 ? '첫 번째 항목입니다' : '위로 이동'}
                      >
                        <ArrowUp className="w-3.5 h-3.5" />
                      </button>

                      {/* Reorder Down Button */}
                      <button
                        type="button"
                        onClick={() => handleMoveDDay(idx, 'down')}
                        disabled={idx === config.ddays.length - 1}
                        className="p-1.5 rounded-lg bg-slate-700/50 hover:bg-blue-600 hover:text-white text-slate-300 disabled:opacity-30 disabled:hover:bg-slate-700/50 disabled:hover:text-slate-300 transition-colors"
                        title={idx === config.ddays.length - 1 ? '마지막 항목입니다' : '아래로 이동'}
                      >
                        <ArrowDown className="w-3.5 h-3.5" />
                      </button>

                      {/* Delete Button */}
                      <button
                        type="button"
                        onClick={() => handleDeleteDDay(d.id)}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-red-400 hover:bg-red-500/10 transition-colors ml-1"
                        title="D-Day 삭제"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        {/* 3. Timetable Editor */}
        {activeTab === 'timetable' && (
          <div className="space-y-4">
            {/* Presets */}
            <div className="flex flex-wrap items-center justify-between gap-2 p-2.5 bg-slate-800/60 rounded-xl border border-slate-700/60">
              <span className={`${sz('text-base','text-xs')} font-semibold text-slate-300`}>시간표 빠른 프리셋:</span>
              <div className="flex gap-1.5">
                <button
                  type="button"
                  onClick={() => applyTimetablePreset('high-teacher')}
                  className={`px-2 py-1 ${sz('text-[14px]','text-[11px]')} bg-slate-700 hover:bg-slate-600 text-slate-200 rounded-md font-medium transition-colors`}
                >
                  고교 교사용
                </button>
                <button
                  type="button"
                  onClick={() => applyTimetablePreset('middle-teacher')}
                  className={`px-2 py-1 ${sz('text-[14px]','text-[11px]')} bg-slate-700 hover:bg-slate-600 text-slate-200 rounded-md font-medium transition-colors`}
                >
                  중학교 교사용
                </button>
                <button
                  type="button"
                  onClick={() => applyTimetablePreset('elem-student')}
                  className={`px-2 py-1 ${sz('text-[14px]','text-[11px]')} bg-slate-700 hover:bg-slate-600 text-slate-200 rounded-md font-medium transition-colors`}
                >
                  초/중 학생용
                </button>
              </div>
            </div>

            {/* Weekly Timetable Grid Editor */}
            <div className="space-y-2">
              {(['월', '화', '수', '목', '금'] as const).map((day) => {
                const dayObj = config.timetable.find((t) => t.day === day) || {
                  day,
                  periods: ['', '', '', '', '', '', ''],
                };
                return (
                  <div key={day} className="p-2.5 bg-slate-800/80 rounded-xl border border-slate-700">
                    <div className={`${sz('text-base','text-xs')} font-bold text-blue-400 mb-2`}>{day}요일 시간표</div>
                    <div className="grid grid-cols-7 gap-1">
                      {dayObj.periods.map((subject, pIdx) => (
                        <div key={pIdx} className="space-y-1">
                          <label className={`${sz('text-[13px]','text-[10px]')} text-slate-400 block text-center`}>
                            {pIdx + 1}교시
                          </label>
                          <input
                            type="text"
                            value={subject}
                            onChange={(e) => handleTimetableChange(day, pIdx, e.target.value)}
                            placeholder="-"
                            className={`w-full px-1 py-1 ${sz('text-[14px]','text-[11px]')} text-center rounded bg-slate-900 border border-slate-700 text-slate-200 focus:outline-none focus:border-blue-500`}
                          />
                        </div>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* 4. Style & Window Settings */}
        {activeTab === 'style' && (
          <div className="space-y-4">
            {/* Themes */}
            <div>
              <label className={`block ${sz('text-base','text-xs')} font-semibold text-slate-300 mb-2`}>
                🎨 위젯 테마 선택
              </label>
              <div className="grid grid-cols-2 gap-2">
                {[
                  { id: 'dark-acrylic', name: '모던 다크 글래스', desc: '세련된 어두운 반투명' },
                  { id: 'light-acrylic', name: '클린 라이트 아크릴', desc: '밝고 산뜻한 화이트' },
                  { id: 'slate-glass', name: '실버 슬레이트', desc: '차분한 스틸 그레이 톤' },
                  { id: 'emerald-glass', name: '에메랄드 포레스트', desc: '편안한 녹색 톤' },
                  { id: 'indigo-glass', name: '딥 인디고 블루', desc: '고급스러운 야간 톤' },
                  { id: 'sakura-glass', name: '사쿠라 핑크', desc: '은은한 벚꽃 핑크 톤' },
                ].map((th) => (
                  <button
                    key={th.id}
                    type="button"
                    onClick={() => onUpdateConfig({ ...config, theme: th.id as WidgetTheme })}
                    className={`p-2.5 rounded-xl border text-left transition-all ${
                      config.theme === th.id
                        ? 'bg-blue-600/20 border-blue-500 text-white shadow-xs'
                        : 'bg-slate-800 border-slate-700 hover:border-slate-600 text-slate-300'
                    }`}
                  >
                    <div className={`${sz('text-base','text-xs')} font-bold flex items-center justify-between`}>
                      <span>{th.name}</span>
                      {config.theme === th.id && <Check className="w-3.5 h-3.5 text-blue-400" />}
                    </div>
                    <div className={`${sz('text-[13px]','text-[10px]')} text-slate-400 mt-0.5`}>{th.desc}</div>
                  </button>
                ))}
              </div>
            </div>

            {/* Font Scale slider */}
            <div>
              <div className={`flex justify-between ${sz('text-base','text-xs')} font-semibold text-slate-300 mb-1`}>
                <span>글씨 크기</span>
                <span className="text-blue-400">{Math.round((config.fontScale ?? 1) * 100)}%</span>
              </div>
              <input
                type="range"
                min="0.85"
                max="1.3"
                step="0.05"
                value={config.fontScale ?? 1}
                onChange={(e) => onUpdateConfig({ ...config, fontScale: parseFloat(e.target.value) })}
                className="w-full h-1.5 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-blue-500"
              />
              <span className={`${sz('text-[13px]','text-[10px]')} text-slate-400 mt-1 block`}>
                위젯 전체 글씨와 요소 크기가 이 비율만큼 함께 커지거나 작아집니다.
              </span>
            </div>

            {/* Opacity slider */}
            <div>
              <div className={`flex justify-between ${sz('text-base','text-xs')} font-semibold text-slate-300 mb-1`}>
                <span>위젯 투명도 (Opacity)</span>
                <span className="text-blue-400">{Math.round(config.opacity * 100)}%</span>
              </div>
              <input
                type="range"
                min="0.6"
                max="1.0"
                step="0.05"
                value={config.opacity}
                onChange={(e) => onUpdateConfig({ ...config, opacity: parseFloat(e.target.value) })}
                className="w-full h-1.5 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-blue-500"
              />
            </div>

            {/* Snap Side */}
            <div>
              <label className={`block ${sz('text-base','text-xs')} font-semibold text-slate-300 mb-2`}>
                자동 스냅 위치
              </label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => onUpdateConfig({ ...config, snapSide: 'left' })}
                  className={`py-2 rounded-xl border ${sz('text-base','text-xs')} font-bold transition-all ${
                    config.snapSide === 'left'
                      ? 'bg-blue-600/20 border-blue-500 text-white'
                      : 'bg-slate-800 border-slate-700 hover:border-slate-600 text-slate-300'
                  }`}
                >
                  ⬉ 왼쪽 상단
                </button>
                <button
                  type="button"
                  onClick={() => onUpdateConfig({ ...config, snapSide: 'right' })}
                  className={`py-2 rounded-xl border ${sz('text-base','text-xs')} font-bold transition-all ${
                    config.snapSide === 'right' || !config.snapSide
                      ? 'bg-blue-600/20 border-blue-500 text-white'
                      : 'bg-slate-800 border-slate-700 hover:border-slate-600 text-slate-300'
                  }`}
                >
                  ⬈ 오른쪽 상단
                </button>
              </div>
              <span className={`${sz('text-[13px]','text-[10px]')} text-slate-400 mt-1 block`}>
                위젯을 드래그하다 손을 떼면 화면 이쪽 방향 상단으로 자동으로 달라붙습니다.
              </span>
            </div>

            {/* Snap Margin */}
            <div>
              <div className={`flex justify-between ${sz('text-base','text-xs')} font-semibold text-slate-300 mb-1`}>
                <span>{config.snapSide === 'left' ? '좌측' : '우측'} 상단 자동 스냅 여백 (Margin)</span>
                <span className="text-blue-400">{config.snapMargin}px</span>
              </div>
              <input
                type="range"
                min="0"
                max="60"
                step="5"
                value={config.snapMargin}
                onChange={(e) => onUpdateConfig({ ...config, snapMargin: parseInt(e.target.value) })}
                className="w-full h-1.5 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-blue-500"
              />
              <span className={`${sz('text-[13px]','text-[10px]')} text-slate-400 mt-1 block`}>
                위젯을 마우스로 옮기다가 손을 떼면 화면 {config.snapSide === 'left' ? '좌측' : '우측'} 상단에 자동으로 달라붙는데, 그때 화면 가장자리와 위젯 사이에 남길 여백 간격입니다.
                <br />
                0px이면 화면 끝에 딱 붙고, 값을 높이면 그만큼 안쪽으로 떨어져서 붙습니다.
              </span>
            </div>

            {/* Toggle Always On Top */}
            <div className="pt-2 border-t border-slate-800 flex items-center justify-between">
              <div>
                <div className={`${sz('text-base','text-xs')} font-bold text-slate-200`}>항상 위에 표시 (Topmost)</div>
                <div className={`${sz('text-[14px]','text-[11px]')} text-slate-400`}>다른 작업 창 위에 위젯을 항상 고정합니다.</div>
              </div>
              <input
                type="checkbox"
                checked={config.alwaysOnTop}
                onChange={(e) => onUpdateConfig({ ...config, alwaysOnTop: e.target.checked })}
                className="w-4 h-4 rounded bg-slate-800 border-slate-700 text-blue-600 focus:ring-0 cursor-pointer"
              />
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
