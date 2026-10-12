# 参考实现的偏好键清单

来源：`_re/extract/Payload/SenPlayer.app/SenPlayer`（砸壳版，sha256 `b2380394a6af7e7f…`）。
抓取方式：`strings -a` 后取 `^k[A-Z]…`，再滤掉 Apple 框架常量与 Swift 混淆片段
——`kCFAllocatorNull`、`kHih8` 这类短片段与设置无关，滤掉后剩 195 条。

**这是键名的清单，不是行为的清单。** 它证明某个设置存在，不说明它的判定逻辑：
「新标记」按什么算新、防遮挡矩形的坐标系、缓存的单位，都要另找证据。

## 分门别类

### 播放器按钮（12）

- `kShowPlayerAspectRatioButton`
- `kShowPlayerChapterButton`
- `kShowPlayerDlnaButton`
- `kShowPlayerFullScreenButton`
- `kShowPlayerListButton`
- `kShowPlayerPipButton`
- `kShowPlayerPlayButton`
- `kShowPlayerScreenButton`
- `kShowPlayerSkipButton`
- `kShowPlayerSpeedButton`
- `kShowPlayerSwitchButton`
- `kShowPlayerSystemTime`

### 屏显 OSD（5）

- `kShowOSDInternetSpeed`
- `kShowOSDOnlyLandscape`
- `kShowOSDPlaySpeed`
- `kShowOSDSystemTime`
- `kShowOSDdisplayCloselyOnBothSides`

### 字幕（21）

- `kFileTypeShowSubtitle`
- `kIgnoringSubtitleEffects`
- `kIsMemorySubtitleStream`
- `kSubtitleBgColor`
- `kSubtitleChineseConversion`
- `kSubtitleFirstLanguage`
- `kSubtitleHDRBrightnessValue`
- `kSubtitleHistory`
- `kSubtitleLoadRule`
- `kSubtitleMaskCenterX`
- `kSubtitleMaskCenterY`
- `kSubtitleMaskHeight`
- `kSubtitleMaskWidth`
- `kSubtitleOpacity`
- `kSubtitleSearchHistoryShow`
- `kSubtitleShadowColor`
- `kSubtitleShadowSize`
- `kSubtitleStrokeColor`
- `kSubtitleStrokeSize`
- `kSubtitleTextColor`
- `kSubtitleVerticalOffset`

### 弹幕（5）

- `kDanmuBottomToFloat`
- `kDanmuMatchWithYear`
- `kDanmuPipEnabled`
- `kDanmuSearchHistoryShow`
- `kDanmuStrokeWidth`

### 手势（13）

- `kDoubleTapBothSidesMode`
- `kDoubleTapCenterMode`
- `kIsGestureBrightness`
- `kIsGestureProgress`
- `kIsGestureResetView`
- `kIsGestureSwitchMedia`
- `kIsGestureVolume`
- `kMacLeftDoubleTapModes`
- `kMacLeftSingTapModes`
- `kMacRightDoubleTapModes`
- `kMacRightSingTapModes`
- `kSingleTapBothSidesMode`
- `kSingleTapCenterMode`

### Emby/Plex/媒体库（25）

- `kEmbyCarouselImageType`
- `kEmbyCommonSortBy`
- `kEmbyCommonSortOrder`
- `kEmbyDetailImageType`
- `kEmbyDisplayPlayButton`
- `kEmbyHidePlayedInLatest`
- `kEmbyMoviesSortBy`
- `kEmbyMoviesSortOrder`
- `kEmbyResumeShowEpisodeImage`
- `kEmbySearchHistory`
- `kEmbyShowCarousel`
- `kEmbyShowContinue`
- `kEmbyShowMyMedia`
- `kEmbyShowRecommended`
- `kEmbyTextAlignment`
- `kMbShowCollection`
- `kPlexCommonSortBy`
- `kPlexCommonSortOrder`
- `kPlexMoviesSortBy`
- `kPlexMoviesSortOrder`
- `kShowMbDoubanLink`
- `kShowMbEpisodeIndicator`
- `kShowMbGlobalSearchButton`
- `kShowMbMediaInfo`
- `kShowMbMoreSilimar`

### 记录/搜索模块（19）

- `kDanmuSearchHistoryShow`
- `kDisplaySearchBox`
- `kEmbySearchHistory`
- `kRecordAudioHistroy`
- `kRecordPlayHistroy`
- `kRecordShowBookmarkFile`
- `kRecordShowBookmarkFolder`
- `kRecordShowCommon`
- `kRecordShowContinue`
- `kRecordShowFavirote`
- `kRecordShowHistroy`
- `kRecordShowScreen`
- `kSearchDriveServerId`
- `kSearchMediaServerId`
- `kSearchShowDrive`
- `kSearchShowMedia`
- `kShowMbGlobalSearchButton`
- `kShowSearchModule`
- `kSubtitleSearchHistoryShow`

### 解码/画质（14）

- `kAliResolutionType`
- `kBaiduResolutionType`
- `kEnableDolbyAtmos`
- `kEnableDolbyP7FEL`
- `kEnableDolbyP7ToP81`
- `kFirstResolution`
- `kIsAspectRatioCorrection`
- `kIsShowFullResolution`
- `kIsShowResolutionFlag`
- `kIsShowSimpleResolutionFlag`
- `kShowPlayerAspectRatioButton`
- `kSubtitleHDRBrightnessValue`
- `kVideoFrameInterpolationEnabled`
- `kVideoUpscalingLevel`

### 网络/缓存（4）

- `kDownloadConcurrentTasks`
- `kIsCloudDriveSpeedLimitToast`
- `kLastHTTPVideoCacheKey`
- `kMacDownloadFolder`

### 其他（83）

- `kAudioEnhancePercent`
- `kAudioEnhancement`
- `kAudioFirstLanguage`
- `kAutoCopyToClipboard`
- `kBackgroundExitPrivacy`
- `kCacheSize`
- `kControlAutoHide`
- `kControlBarPosition`
- `kDefaultFileContent`
- `kDefaultLaunchCardId`
- `kDisplayConnectionStatus`
- `kDisplayMeidaStatistics`
- `kDisplayWatchTime`
- `kEnableOnlyPlaying`
- `kEnumerateSmbHiddenShare`
- `kFastBackwordTime`
- `kFastForwordTime`
- `kFileTypeShowMusic`
- `kFileTypeShowOther`
- `kFileTypeShowPicture`
- `kFileTypeShowVideo`
- `kFolderIndependentSort`
- `kFvP`
- `kGlobalAudioDelay`
- `kGsP8`
- `kHapticDragProgress`
- `kHapticLongPress`
- `kHapticSliderProgress`
- `kHardwarePasswordEnable`
- `kIb8K`
- `kIsAutoSaveToAlbum`
- `kIsCreatePreview`
- `kIsForegroundAutoPlay`
- `kIsMemoryAudioStream`
- `kIsPreviewScaleToFill`
- `kIsProgressAlwaysOn`
- `kIsRememberPosition`
- `kIsRememberZoomSize`
- `kIsShowBasicMediaInfo`
- `kIsShowPlayToast`
- `kIsShowSkipOPEDToast`
- `kIsSupportBackgroundPlay`
- `kK0J`
- `kListShowProgress`
- `kLockControlMode`
- `kLockShowPreview`
- `kLongPressPlayExitPlayer`
- `kLongPressSpeedLeftTime`
- `kLongPressSpeedRightTime`
- `kMacNumberKeySwitchSpeed`
- `kMacScreenshotFolder`
- `kMainShowPrivacy`
- `kManualLandscapeOrientation`
- `kMatchWindowRatio`
- `kMenuTopShowFullName`
- `kMinimizeTabOnScroll`
- `kMixedContentTab`
- `kOrientationMode`
- `kPasswordUseForApp`
- `kPipDanmakuScale`
- `kPlayerSortOrder`
- `kPreviewImageThread`
- `kPreviewProgress`
- `kPrioritizeBuiltInThumbnails`
- `kPrivacyPassword`
- `kProgressTimeMode`
- `kScreenshotFormat`
- `kScreenshotJpegLevel`
- `kServerListOrderConfig`
- `kShowCommunityRating`
- `kShowControlBarWhenTouched`
- `kShowRecordModule`
- `kShowUnplayedItemCount`
- `kSwitchVideoMode`
- `kThumbniShowTime`
- `kThumbnilShowSize`
- `kTitleShowAmount`
- `kTraditionalToSimplified`
- `kU9H`
- `kUpDownSwitchVideo`
- `kUseAppBrightness`
- `kUseLockControlOnlyPublic`
- `kY8B`

