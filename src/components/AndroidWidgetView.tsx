import React, { useState, useEffect } from 'react';
import QRCode from 'qrcode';
import { WidgetConfig } from '../types';
import { generateAndroidProjectFiles, AndroidCodeFiles } from '../utils/androidGenerator';
import { buildImportDeepLink } from '../utils/deepLink';
import {
  Tablet,
  Smartphone,
  Copy,
  Check,
  Download,
  FileCode,
  Sparkles,
  RefreshCw,
  Calendar,
  Clock,
  Utensils,
  BookOpen,
  FolderTree,
  PlayCircle,
  ShieldCheck,
  CheckCircle2,
  ExternalLink,
  Info,
  QrCode
} from 'lucide-react';

interface AndroidWidgetViewProps {
  config: WidgetConfig;
}

type AndroidFileName = keyof AndroidCodeFiles;

const ApplyToDeviceCard: React.FC<{ config: WidgetConfig }> = ({ config }) => {
  const [showQr, setShowQr] = useState(false);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [qrError, setQrError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const deepLink = buildImportDeepLink(config);

  useEffect(() => {
    if (!showQr) return;
    let cancelled = false;
    QRCode.toDataURL(deepLink, { margin: 1, width: 260, color: { dark: '#0F172A', light: '#FFFFFF' } })
      .then((url) => {
        if (!cancelled) setQrDataUrl(url);
      })
      .catch(() => {
        if (!cancelled) setQrError('QR코드를 생성하지 못했습니다.');
      });
    return () => {
      cancelled = true;
    };
  }, [showQr, deepLink]);

  const handleApplyHere = () => {
    window.location.href = deepLink;
  };

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(deepLink);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy link', err);
    }
  };

  return (
    <div className="p-4 rounded-2xl bg-emerald-950/30 border border-emerald-500/30 space-y-3">
      <div className="flex items-start gap-3">
        <Smartphone className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
        <div className="space-y-1 flex-1">
          <div className="font-bold text-white text-sm">
            학교 / 시간표 / D-Day를 이미 설치된 앱에 바로 적용
          </div>
          <p className="text-xs text-slate-400 leading-relaxed">
            앱을 다시 빌드하거나 재설치하지 않고, 지금 이 화면의 설정을 바로 위젯에 반영합니다.
            <strong className="text-emerald-300"> 위젯 앱이 이미 설치된 기기</strong>의 브라우저에서 이 스튜디오를 열고 눌러야 합니다.
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={handleApplyHere}
          className="px-3 py-1.5 rounded-xl text-xs sm:text-sm font-bold bg-emerald-600 hover:bg-emerald-500 text-white flex items-center gap-1.5 transition-all shadow-md shadow-emerald-600/20"
        >
          <Smartphone className="w-3.5 h-3.5" />
          <span>이 기기에 적용하기</span>
        </button>

        <button
          type="button"
          onClick={() => setShowQr((v) => !v)}
          className="px-3 py-1.5 rounded-xl text-xs sm:text-sm font-bold bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-700 flex items-center gap-1.5 transition-all"
        >
          <QrCode className="w-3.5 h-3.5" />
          <span>{showQr ? 'QR코드 닫기' : '다른 기기로 전송 (QR코드)'}</span>
        </button>

        <button
          type="button"
          onClick={handleCopyLink}
          className="px-3 py-1.5 rounded-xl text-xs sm:text-sm font-bold bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-700 flex items-center gap-1.5 transition-all"
        >
          {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
          <span>{copied ? '링크 복사됨' : '링크만 복사'}</span>
        </button>
      </div>

      {showQr && (
        <div className="flex flex-col items-center gap-2 p-4 rounded-xl bg-slate-950/60 border border-slate-800">
          {qrDataUrl ? (
            <img src={qrDataUrl} alt="위젯 설정 적용 QR코드" className="w-48 h-48 rounded-lg bg-white p-2" />
          ) : qrError ? (
            <span className="text-xs text-red-400">{qrError}</span>
          ) : (
            <span className="text-xs text-slate-400">QR코드 생성 중...</span>
          )}
          <p className="text-[11px] text-slate-400 text-center leading-relaxed max-w-xs">
            위젯 앱이 설치된 다른 기기(태블릿 등)의 카메라로 이 QR코드를 스캔하면 앱이 열리며 설정이 바로 적용됩니다.
          </p>
        </div>
      )}
    </div>
  );
};

export const AndroidWidgetView: React.FC<AndroidWidgetViewProps> = ({ config }) => {
  const [deviceMode, setDeviceMode] = useState<'tablet' | 'phone'>('tablet');
  const [selectedFile, setSelectedFile] = useState<AndroidFileName>('SchoolWidget.kt');
  const [copiedFile, setCopiedFile] = useState<string | null>(null);

  const files = generateAndroidProjectFiles(config);

  const fileDescriptions: Record<AndroidFileName, string> = {
    'SchoolWidget.kt': 'Jetpack Glance 기반 안드로이드 홈 화면 위젯 UI (시간표, 급식, D-Day)',
    'NeisMealService.kt': '교육부 나이스(NEIS) Open API 실시간 파싱 및 13:30 자동 다음날 전환 서비스',
    'MainActivity.kt': '태블릿/폰 설정 메인 액티비티 & WorkManager 스케줄러 등록',
    'WidgetUpdateWorker.kt': '15분마다 배터리 절약 백그라운드 위젯 자동 갱신 워커',
    'SchoolWidgetReceiver.kt': '안드로이드 OS 위젯 브로드캐스트 수신 클래스',
    'AndroidManifest.xml': '인터넷 권한 및 위젯 프로바이더 등록 메타데이터',
    'build.gradle.kts': 'Jetpack Glance, Compose, WorkManager, OkHttp 의존성 설정',
    'widget_config.json': '지금 설정된 시간표 & D-Day. 저장소의 android/app/src/main/assets/widget_config.json 을 이 파일로 덮어쓰고 푸시하면 자동 빌드된 위젯에 바로 반영됩니다 (코딩 불필요).',
    'README_ANDROID.md': '안드로이드 스튜디오 3분 빌드 & 태블릿 위젯 추가 가이드',
  };

  const handleCopy = async (filename: AndroidFileName) => {
    try {
      await navigator.clipboard.writeText(files[filename]);
      setCopiedFile(filename);
      setTimeout(() => setCopiedFile(null), 2000);
    } catch (err) {
      console.error('Failed to copy', err);
    }
  };

  const handleDownloadFile = (filename: AndroidFileName) => {
    const content = files[filename];
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-6 text-slate-200">
      {/* Top Banner */}
      <div className="p-4 sm:p-5 rounded-2xl bg-gradient-to-r from-emerald-950/60 via-slate-900 to-blue-950/60 border border-emerald-500/30 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2 text-emerald-400 font-bold text-base">
            <Tablet className="w-5 h-5" />
            <span>안드로이드 태블릿 & 스마트폰용 네이티브 위젯 (Jetpack Glance / Kotlin)</span>
          </div>
          <p className="text-sm text-slate-300 leading-relaxed">
            구글 공식 모던 위젯 프레임워크인 <strong>Jetpack Glance</strong>를 사용하여 안드로이드 태블릿 홈 화면에 최적화된 고해상도 위젯 코드를 생성합니다.
          </p>
        </div>

        {/* Device Switcher */}
        <div className="flex items-center gap-1.5 p-1 bg-slate-950/80 rounded-xl border border-slate-800 shrink-0">
          <button
            type="button"
            onClick={() => setDeviceMode('tablet')}
            className={`px-3 py-1.5 rounded-lg text-sm font-semibold flex items-center gap-1.5 transition-all ${
              deviceMode === 'tablet'
                ? 'bg-emerald-600 text-white shadow-md'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Tablet className="w-4 h-4" />
            <span>태블릿 뷰 (10~14인치)</span>
          </button>
          <button
            type="button"
            onClick={() => setDeviceMode('phone')}
            className={`px-3 py-1.5 rounded-lg text-sm font-semibold flex items-center gap-1.5 transition-all ${
              deviceMode === 'phone'
                ? 'bg-emerald-600 text-white shadow-md'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Smartphone className="w-4 h-4" />
            <span>스마트폰 뷰</span>
          </button>
        </div>
      </div>

      {/* GitHub Actions 자동 빌드 안내 (Android Studio 불필요) */}
      <div className="p-4 rounded-2xl bg-blue-950/30 border border-blue-500/30 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div className="flex items-start gap-3">
          <ShieldCheck className="w-5 h-5 text-blue-400 shrink-0 mt-0.5" />
          <div className="space-y-1">
            <div className="font-bold text-white text-sm">
              더 간단한 방법: Android Studio 없이 완성된 APK만 다운로드
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">
              저장소의 <code className="text-blue-300">android/</code> 폴더는 GitHub Actions가 자동으로 빌드하는 완전한 프로젝트입니다.
              누구나 이 저장소를 직접 건드릴 필요 없이, <strong>Releases</strong> 탭에서 <code className="text-blue-300">app-debug.apk</code>
              파일을 받아 태블릿에 한 번 설치하면 됩니다 (Android Studio·USB 연결 불필요). 학교/시간표/D-Day는
              앱을 다시 설치하지 않고 아래 <strong>[이 기기에 적용하기]</strong>로 바로 반영할 수 있습니다.
            </p>
          </div>
        </div>
        <a
          href="https://github.com/heesung0128-hub/School-Widget-Studio-for-class-/releases"
          target="_blank"
          rel="noopener noreferrer"
          className="px-3 py-1.5 rounded-xl text-xs sm:text-sm font-bold bg-blue-600 hover:bg-blue-500 text-white flex items-center gap-1.5 transition-all shadow-md shadow-blue-600/20 shrink-0"
        >
          <ExternalLink className="w-3.5 h-3.5" />
          <span>Releases에서 APK 받기</span>
        </a>
      </div>

      {/* 설치된 앱에 시간표/D-Day 바로 적용 (딥링크 + QR 백업) */}
      <ApplyToDeviceCard config={config} />

      {/* Main Grid: Left Tablet Mockup Preview / Right Code & Studio Export */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left: Device Simulator (5 cols) */}
        <div className="lg:col-span-5 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-base font-bold text-white flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-emerald-400" />
              <span>안드로이드 홈 화면 위젯 시뮬레이션</span>
            </h3>
            <span className="text-[13px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-300 border border-emerald-500/20">
              실시간 렌더링
            </span>
          </div>

          {/* Android Tablet Frame */}
          <div
            className={`w-full bg-slate-950 p-4 rounded-3xl border-4 border-slate-800 shadow-2xl relative overflow-hidden transition-all ${
              deviceMode === 'tablet' ? 'min-h-[460px]' : 'max-w-xs mx-auto min-h-[460px]'
            }`}
          >
            {/* Tablet Camera dot */}
            <div className="absolute top-2 left-1/2 -translate-x-1/2 w-2 h-2 rounded-full bg-slate-800" />

            {/* Android Wallpaper background */}
            <div className="h-full w-full rounded-2xl bg-gradient-to-br from-slate-900 via-indigo-950/40 to-slate-900 p-3 flex flex-col justify-center border border-slate-800/80">
              {/* Android Status Bar */}
              <div className="flex items-center justify-between text-[11px] text-slate-400 px-1 mb-3">
                <span className="font-semibold">09:41</span>
                <div className="flex items-center gap-2">
                  <span>5G</span>
                  <span>100% 🔋</span>
                </div>
              </div>

              {/* The Actual Android Glance Widget on Tablet */}
              <div className="w-full bg-slate-900/90 backdrop-blur-md rounded-2xl p-4 border border-slate-700/80 shadow-xl space-y-3">
                {/* Header */}
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-[11px] text-slate-400 font-medium">9월 1일 (월)</div>
                    <div className="text-base font-bold text-white flex items-center gap-1.5">
                      <span>{config.school.schoolName}</span>
                    </div>
                  </div>
                  {config.ddays.length > 0 && (
                    <div className="px-2.5 py-1 rounded-lg bg-blue-500/20 border border-blue-500/30 text-blue-300 font-bold text-xs">
                      {config.ddays[0].title.slice(0, 5)} D-30
                    </div>
                  )}
                </div>

                {/* Current Class Badge */}
                <div className="p-2.5 rounded-xl bg-slate-800/80 border border-slate-700/60 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Clock className="w-4 h-4 text-sky-400" />
                    <span className="text-xs text-sky-300 font-bold">현재 1교시 (09:00~09:50):</span>
                    <span className="text-xs text-white font-medium">
                      {config.timetable[0]?.periods[0] || '수업'}
                    </span>
                  </div>
                  <span className="text-[11px] text-emerald-400 font-semibold">진행중</span>
                </div>

                {/* Meals */}
                <div className="p-3 rounded-xl bg-slate-800/80 border border-slate-700/60 space-y-1.5">
                  <div className="flex items-center justify-between text-xs">
                    <div className="flex items-center gap-1.5 text-amber-300 font-bold">
                      <Utensils className="w-3.5 h-3.5" />
                      <span>오늘의 급식 (나이스 연동)</span>
                    </div>
                    <span className="text-[11px] text-slate-400">685.2 kcal</span>
                  </div>
                  <div className="text-xs text-slate-200 leading-relaxed">
                    친환경차수수밥, 돈육김치찌개, 안동찜닭, 달걀말이, 깍두기
                  </div>
                </div>

                {/* Quick Action Footer */}
                <div className="flex items-center justify-between pt-1 text-[11px] text-slate-400">
                  <span className="flex items-center gap-1 text-slate-500">
                    <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                    자동 동기화 (15분 주기)
                  </span>
                  <span className="text-sky-400 font-semibold cursor-pointer hover:underline">
                    🔄 새로고침
                  </span>
                </div>
              </div>

              {/* Android Home Bar */}
              <div className="mt-3 flex justify-center">
                <div className="w-24 h-1 rounded-full bg-slate-700" />
              </div>
            </div>
          </div>

          <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800 text-xs text-slate-300 space-y-1">
            <div className="font-semibold text-emerald-400 flex items-center gap-1">
              <Info className="w-3.5 h-3.5" />
              <span>안드로이드 위젯의 장점</span>
            </div>
            <p className="text-slate-400 leading-relaxed">
              태블릿의 홈 화면 크기에 맞춰 <strong>2x2, 4x2, 4x4 등 자유로운 리사이징</strong>이 가능하며, 교탁/교무실 거치용 태블릿에서 화면을 켤 때마다 실시간으로 시간표와 급식이 자동 갱신됩니다.
            </p>
          </div>
        </div>

        {/* Right: Code Viewer & Export (7 cols) */}
        <div className="lg:col-span-7 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-base font-bold text-white flex items-center gap-2">
              <FileCode className="w-4 h-4 text-blue-400" />
              <span>Android Studio 프로젝트 소스 파일</span>
            </h3>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => handleCopy(selectedFile)}
                className="px-3 py-1.5 rounded-xl text-xs sm:text-sm font-bold bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-700 flex items-center gap-1.5 transition-all"
              >
                {copiedFile === selectedFile ? (
                  <Check className="w-3.5 h-3.5 text-emerald-400" />
                ) : (
                  <Copy className="w-3.5 h-3.5" />
                )}
                <span>{copiedFile === selectedFile ? '복사됨!' : '현재 파일 복사'}</span>
              </button>

              <button
                type="button"
                onClick={() => handleDownloadFile(selectedFile)}
                className="px-3 py-1.5 rounded-xl text-xs sm:text-sm font-bold bg-blue-600 hover:bg-blue-500 text-white flex items-center gap-1.5 transition-all shadow-md shadow-blue-600/20"
              >
                <Download className="w-3.5 h-3.5" />
                <span>{selectedFile} 다운로드</span>
              </button>
            </div>
          </div>

          {/* File Tab Selector */}
          <div className="flex gap-1.5 overflow-x-auto pb-1 bg-slate-950 p-1.5 rounded-xl border border-slate-800">
            {(Object.keys(files) as AndroidFileName[]).map((filename) => (
              <button
                key={filename}
                type="button"
                onClick={() => setSelectedFile(filename)}
                className={`px-3 py-1.5 rounded-lg text-xs font-mono font-semibold shrink-0 transition-all ${
                  selectedFile === filename
                    ? 'bg-blue-600 text-white shadow-sm'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
                }`}
              >
                {filename}
              </button>
            ))}
          </div>

          {/* Current File Description */}
          <div className="px-3 py-2 rounded-xl bg-slate-900/60 border border-slate-800 flex items-center gap-2 text-xs text-slate-300">
            <span className="w-2 h-2 rounded-full bg-blue-400 shrink-0" />
            <span className="font-mono text-blue-300 font-semibold">{selectedFile}</span>
            <span className="text-slate-400">— {fileDescriptions[selectedFile]}</span>
          </div>

          {/* Code Viewer Box */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl flex flex-col h-[400px]">
            <div className="flex-1 p-4 overflow-y-auto font-mono text-xs text-slate-300 bg-slate-950/70 leading-relaxed select-text">
              <pre className="whitespace-pre-wrap break-all">
                {files[selectedFile]}
              </pre>
            </div>
          </div>

          {/* 3-Step Setup Guide Accordion */}
          <div className="p-4 rounded-2xl bg-slate-900 border border-slate-800 space-y-3">
            <div className="font-bold text-white text-sm flex items-center gap-2">
              <PlayCircle className="w-4 h-4 text-emerald-400" />
              <span>Android Studio에서 빌드하는 초간단 3단계 방법</span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
              <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800/80 space-y-1">
                <div className="font-bold text-emerald-400">1. 새 프로젝트 생성</div>
                <p className="text-slate-400">
                  Android Studio ➔ <strong>New Project</strong> ➔ <strong>Empty Activity (Compose)</strong> 생성
                </p>
              </div>

              <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800/80 space-y-1">
                <div className="font-bold text-blue-400">2. 파일 붙여넣기</div>
                <p className="text-slate-400">
                  위 탭의 <code>SchoolWidget.kt</code>, <code>NeisMealService.kt</code> 등 7개 파일을 복사해 넣습니다.
                </p>
              </div>

              <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800/80 space-y-1">
                <div className="font-bold text-purple-400">3. 태블릿에 설치</div>
                <p className="text-slate-400">
                  태블릿 연결 후 <strong>Run(▶)</strong> ➔ 홈 화면 길게 누르고 <strong>[학교 위젯]</strong> 추가!
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
