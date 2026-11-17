# CU: привязки ассетов к логике, исправление 0.3.5

Исследован установленный оригинал, не только имена изображений/звуков:
`C:\Program Files (x86)\Steam\steamapps\common\Casualties Unknown\CasualtiesUnknown_Data\Managed\Assembly-CSharp.dll`.
SHA-256: `48c7f2296fc4a659faae3b1822884ca5b7ff73d6767ef159e6a6c2bc0b2d38f1`.
MVID: `a598f273-a4d7-465b-9ef9-4ff70b39d5e4`.

Свежая статическая декомпиляция dnSpyEx: PlayerCamera, Body, ECGVisualizer,
BandageMinigame, SyringeMinigame, Item, Sound, MinigameBase, Talker,
TourniquetScript, MusicManager. PE/CLI и вызовы дополнительно проверены REA:
`ev_c1b3e3fdc9e0027db77022981d0068f6e9b6992e8bbfcc5bcb7ff1ec8165dfa5`,
`ev_233ef28338bc71fde14c23a616ee6abd9d7038c4eb94c7e4bc82d7f15db00b58`.
Это координаты этой сборки, не универсальные токены других версий.

| Код оригинала | Проверенная ссылка/логика | Исправление мода |
|---|---|---|
| PlayerCamera.HandleUnconsciousScreen, 0x060005B5 | sleepFill → level1/2023 → sharedassets1/443 `sleepcircle`; Filled/Radial360/Top/clockwise | Маскируется сам кольцевой спрайт; сплошной `Circle` не рисуется |
| CanvasScaler Canvas | reference 1920×1080, matchHeight=1; SleepFill rect 447×447 | Диаметр 447/1080 экранной высоты, не константа 78 GUI px |
| consciousnessECG → level1/1771 | textureSize 139×23, RawImage 417×69, цвет/активность от sleepFill | Отдельная сетка и белый график, совместное появление после затемнения |
| ECGVisualizer.Update + Body.GetECGHeight | 28 ms, затухание 0.985; исходная AnimationCurve из Body prefab resources/11982 | Cubic Hermite по исходным ключам, общая фаза изображения и звука |
| PlayerCamera.LastStandSequence, 0x06000579 | LastStand — один Image, ноль дочерних текстов; heartbeat при входе, drone при timer>2, кадры 8 fps до 6.7 s, fade .25/s | Нет добавленного красного заголовка; дрон отложен; четырёхсекундный fade не обрывается; unscaled clock |
| Body.Update + isCriticallyDying, 0x06000075 | один heartthump / heartthump-heavy / heartthump-heavy-monitor по состоянию и открытому wound view | Единственный выбранный звук на удар; нет двух одновременно |
| MusicManager.Update, critSource / critSourceUnc | prefab AudioSource loop=true → criticalloop / criticalloop-unconscious, crossfade .5/s | Настоящие управляемые циклы, не one-shot при любой потере сознания |
| BandageMinigame.Start/Update/PhysicsUpdate | Outer sprite rect 200×200 (packed 192×192); Line rect 300×300 (packed 54×25), roll 104×104 | Восстановлены trim offsets и полные холсты; указатель принадлежит бинтованию, не шине |
| SyringeMinigame.Update / prefab AudioSource | syringeLoop loop=true; volume=depth, pitch=1.6-condition; entry -219, end -345; fill снизу; excessive sideways → bullethit | Один цикл; исходные геометрические отношения и цвет; остановка при отпускании/выходе, звук в ветке ошибки |
| Item splint useLimbAction | splinticon — накладка на конечность, Sound.Play(splint) при применении | Переносится реальный предмет; накладка после установки; один звук завершения |
| Item tourniquet useLimbAction + TourniquetScript | tourniquetIcon — накладка; Sound.Play отсутствует | Нет выдуманного применения звука шины к жгуту |

## Явные адаптации, не «точная копия»

- Система тела, расход, серверный бюджет процедур и Last Standing остаются Minecraft-моделью.
- Переносятся только известные физиологические поля. Давление, фибрилляция,
  температура и brainDying не выдумываются из общего процента condition.
- Критическое состояние по доступным аналогам: oxygen<.5, blood<.3 при активном
  кровотечении, явный Last Standing. Простая потеря сознания от боли не равна критической смерти.
- По прямому предыдущему требованию пользователя обычный пульсовой HUD/ЭКГ/BPM
  остаются только в инвентаре. Чёрный экран и радиальная шкала доступны вне него.
- Нижнее число BPM в круге — читаемый Minecraft-показатель; исходный текст CU был `Zzz...`.
- Шина и жгут в CU применяются напрямую. Их интерактивные задания — адаптация;
  два простых виджета закрепления не выдаются за исходные CU-ассеты.
- Звуковые уровни адаптированы к MASTER Minecraft; глобальное приглушение остальных
  модов/ванильных звуков во время Last Standing не навязывается.

Полные C#, команды декомпиляции, хеши, сериализованные ссылки/поля и проверки:
`C:\Users\thuam\Desktop\projects\decaying-aftermath\verification\asset-logic-0.3.5`.
Актуальное происхождение экспортов входит в JAR как `cu_asset_logic_provenance.json`.

Minecraft и CU не запускались. Headless-проверки исполняют настоящие клиентские
обработчики на записывающих двойниках API. Это проверка веток, вызовов, координат,
ресурсов и завершения циклов, **не наблюдение игрового окна или динамиков**.
