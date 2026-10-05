package com.limi.tvdesktop
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.relocation.*

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.chrisbanes.haze.*
import java.text.SimpleDateFormat
import java.util.*
internal object DetailOrigin { var bounds=Rect(64f,650f,384f,830f); var restoreFocus:()->Unit={}; var retainedFocus by mutableStateOf<FocusRequester?>(null); var returnProgress by mutableStateOf(0f); var returning by mutableStateOf(false) }
// Monotonic entrance: 800ms, with no overshoot or rebound.
private fun coverTransition()=tween<Float>(800,easing=CubicBezierEasing(.16f,1f,.3f,1f))
// Return: collapse back into the card with a soft spring settle (matches the focus zoom amplitude).
private fun returnMotion()=spring<Float>(dampingRatio=.75f,stiffness=130f,visibilityThreshold=.001f)
/** Detail page rhythm, measured from the reference design (841pt wide canvas). */
internal object DetailDesign { val inset=72.dp; val sectionGap=32.dp; val titleGap=LibraryDesign.titleGap; val shelfPad=14.dp; val cardGap=28.dp }
internal fun isDetailMedia(m:DemoMedia)=m.realItem != null || (m !in DemoLibrary.music && m !in DemoLibrary.memories && m !in DemoLibrary.categories)
@Composable internal fun MediaDetailPage(media:DemoMedia,artwork:DemoArtwork,registerClose:((()->Unit)?)->Unit,onDismiss:(()->Unit)->Unit,onPlayItem:((MediaItemInfo)->Unit)?=null){
 val scope=rememberCoroutineScope(); val expansion=remember(media){Animatable(0f)}; val origin=remember(media){DetailOrigin.bounds}; val restore=remember(media){DetailOrigin.restoreFocus}
 var exiting by remember{mutableStateOf(false)}; var ready by remember{mutableStateOf(false)}; val scroll=rememberLazyListState(); val play=remember{FocusRequester()}; val shelf=remember{FocusRequester()}; val castFocus=remember{FocusRequester()}; val similarFocus=remember{FocusRequester()}; val infoFocus=remember{FocusRequester()}
 var menu by remember{mutableStateOf<String?>(null)}; var season by remember{mutableIntStateOf(1)}
 var navigationJob by remember{mutableStateOf<kotlinx.coroutines.Job?>(null)}
 fun navigate(block:suspend kotlinx.coroutines.CoroutineScope.()->Unit){navigationJob?.cancel();navigationJob=scope.launch(block=block)}
 val back=remember{FocusRequester()};val mediaInfoFocus=remember{FocusRequester()};val focusManager=LocalFocusManager.current;val density=LocalDensity.current
 fun moveVertical(key:Key):Boolean{
  if(menu!=null||exiting||!ready)return false
  val down=key==Key.DirectionDown
  if(focusManager.moveFocus(if(down)FocusDirection.Down else FocusDirection.Up))return true
  if(if(down)scroll.canScrollForward else scroll.canScrollBackward){navigate{scroll.animateScrollBy(with(density){(if(down)240.dp else (-240).dp).toPx()})}}
  else if(!down)back.requestFocus()
  return true
 }
 val context=LocalContext.current; val prefs=remember{context.getSharedPreferences("favorites",0)}; var favorite by remember(media){mutableStateOf(prefs.getBoolean(media.title,false))}
 val glassSource=remember{HazeState()};val wallpaperSource=remember{HazeState()};val topBarSource=remember{HazeState()};var legacyGlass by remember(media){mutableStateOf<ImageBitmap?>(null)}
 LaunchedEffect(media,RenderPerformance.staticBlur){legacyGlass=if(RenderPerformance.staticBlur)withContext(Dispatchers.Default){artwork.blurredWallpaper(media)}else null}

 var realEpisodes by remember(media) { mutableStateOf<List<EpisodeInfo>>(emptyList()) }
 LaunchedEffect(media) {
     val real = media.realItem
     val sid = real?.seriesId ?: if (real?.mediaType in listOf("Series", "tvshows")) real?.id else null
     if (!sid.isNullOrBlank()) {
         withContext(Dispatchers.IO) {
             val eps = MediaLibraryManager.cachedEpisodes(real?.accountId.orEmpty(), sid)
             withContext(Dispatchers.Main) {
                 if (eps.isNotEmpty()) realEpisodes = eps
             }
         }
     }
 }

 val castPhotos by produceState(emptyList<ImageBitmap>(),artwork){value=withContext(Dispatchers.Default){listOf(R.drawable.cast_zhouyutong,R.drawable.cast_jingboran,R.drawable.cast_zhouyutong,R.drawable.cast_zhangli,R.drawable.cast_yongmei,R.drawable.cast_jingboran,R.drawable.cast_zhangli).map{artwork.castImage(it)}}}
 val series=media.detail.contains("集")||media.detail.contains("电视剧")||media.title in listOf("暗涌","长安月","葬送的芙莉莲","我们的星球","地球脉动 III")||media.realItem?.mediaType in listOf("Series","Episode")||realEpisodes.isNotEmpty()
 val tabs=if(series)listOf("剧集","演职人员","类似作品","更多信息")else listOf("演职人员","类似作品","更多信息")
 val real=media.realItem; val isReal=real!=null
 var itemDetail by remember(media){mutableStateOf<MediaDetailInfo?>(null)}
 var similarItems by remember(media){mutableStateOf<List<MediaItemInfo>>(emptyList())}
 var previewMedia by remember{mutableStateOf<MediaItemInfo?>(null)}
 LaunchedEffect(media){
  val r=media.realItem?:return@LaunchedEffect
  withContext(Dispatchers.IO){
   val d=MediaLibraryManager.cachedItemDetail(r.accountId,r.id)
   val s=MediaLibraryManager.cachedSimilar(r.accountId,r.id)
   withContext(Dispatchers.Main){itemDetail=d;similarItems=s}
  }
 }
 val realYear=itemDetail?.productionYear.orEmpty().ifBlank{real?.year.orEmpty()}
 val realGenres=itemDetail?.genres.orEmpty().ifEmpty{real?.genre.orEmpty().split(" / ").filter{it.isNotBlank()}}
 val realOverview=itemDetail?.overview.orEmpty().ifBlank{real?.overview.orEmpty()}
 val realLogoUrl=real?.logoUrl.orEmpty()
 val realOriginalTitle=itemDetail?.originalTitle.orEmpty().takeIf{isReal&&it.isNotBlank()&&it!=media.title}.orEmpty()
 val detailPeople=itemDetail?.people.orEmpty()
 val castPeople=detailPeople.filter{it.type=="Actor"}.ifEmpty{if(isReal)detailPeople else emptyList()}
 val seasons=realEpisodes.map{it.seasonNumber}.distinct().sorted()
 val showEpisodes=series&&(if(isReal)realEpisodes.isNotEmpty() else true)
 val showCast=if(isReal)castPeople.isNotEmpty() else true
 val showSimilar=if(isReal)similarItems.isNotEmpty() else true
 val detailRows1=if(isReal)buildList{
  realOriginalTitle.takeIf{it.isNotBlank()}?.let{add("原始标题" to it)}
  itemDetail?.premiereDate?.takeIf{it.isNotBlank()}?.let{add("首播时间" to it)}
  itemDetail?.runtimeMs?.takeIf{it>0}?.let{add("时长" to formatRuntimeText(it))}
  (if(realEpisodes.isNotEmpty())realEpisodes.size else itemDetail?.recursiveItemCount?:0).takeIf{it>0}?.let{add("集数" to (it.toString()+" 集"))}
  realGenres.takeIf{it.isNotEmpty()}?.let{add("类型" to it.joinToString(" / "))}
  itemDetail?.countries.orEmpty().takeIf{it.isNotEmpty()}?.let{add("国家 / 地区" to it.joinToString(" / "))}
  itemDetail?.studios.orEmpty().takeIf{it.isNotEmpty()}?.let{add("制作公司" to it.joinToString(" / "))}
  itemDetail?.officialRating?.takeIf{it.isNotBlank()}?.let{add("分级" to it)}
  itemDetail?.communityRating?.takeIf{it.isNotBlank()}?.let{add("评分" to it)}
  itemDetail?.status?.takeIf{it.isNotBlank()}?.let{add("状态" to if(it=="Continuing")"连载中" else it)}
 }else listOf("原始标题" to media.title,"首播时间" to "2024 年 3 月 15 日","集数 / 时长" to if(series)"12 集"else"155 分钟","类型" to if(series)"剧情 / 家庭"else"电影 / 剧情","国家 / 地区" to "中国","语言" to "中文","标签" to "成长 / 海岸 / 生活")
 val detailRows2=if(isReal)buildList{
  detailPeople.filter{it.type=="Director"}.map{it.name}.distinct().takeIf{it.isNotEmpty()}?.let{add("导演" to it.joinToString("、"))}
  detailPeople.filter{it.type=="Writer"}.map{it.name}.distinct().takeIf{it.isNotEmpty()}?.let{add("编剧" to it.joinToString("、"))}
  castPeople.map{it.name}.distinct().takeIf{it.isNotEmpty()}?.let{add("主演" to it.take(8).joinToString("、"))}
  realOverview.takeIf{it.isNotBlank()}?.let{add("简介" to it)}
 }else listOf("导演" to "演示资料","编剧" to "演示资料","主演" to "林夏、陈牧驰、周雨彤","简介" to "一段关于相遇与成长的故事。\n这些信息仅用于页面展示，后续将替换为真实媒体资料。")
 val showInfo=if(isReal)(detailRows1.isNotEmpty()||detailRows2.isNotEmpty()) else true
 val episodeWithSource=realEpisodes.firstOrNull{it.mediaSource!=null}
 val mediaInfoColumns=if(isReal)buildList{
  val src=itemDetail?.mediaSource?:episodeWithSource?.mediaSource
  if(src!=null){
   src.video?.let{v->add(listOf("视频",v.displayTitle.ifBlank{v.codec.uppercase()},if(v.width>0&&v.height>0)(v.width.toString()+" × "+v.height.toString())else"").filter{it.isNotBlank()}.joinToString("\n"))}
   src.audios.firstOrNull()?.let{a->val ch=when(a.channels){1->"单声道";2->"立体声";6->"5.1";8->"7.1";else->if(a.channels>0)(a.channels.toString()+" 声道")else""};add(listOf("音频",a.displayTitle.ifBlank{a.codec.uppercase()},ch).filter{it.isNotBlank()}.joinToString("\n"))}
   if(src.subtitles.isNotEmpty()){val langs=src.subtitles.mapNotNull{it.language.ifBlank{it.displayTitle.ifBlank{null}}}.distinct();add(listOf("字幕",langs.joinToString(" / ").ifBlank{"内嵌"}).filter{it.isNotBlank()}.joinToString("\n"))}
   formatBytes(src.sizeBytes).takeIf{it.isNotBlank()}?.let{add(listOf("文件大小",it,src.container.uppercase()).filter{it.isNotBlank()}.joinToString("\n"))}
  }
 }else listOf("视频\n4K · HEVC\n3840 × 2160","音频\nDolby Atmos\n中文 5.1","字幕\n简体中文 / English\nSRT · 内嵌","文件大小\n约 1.2 GB / 集\nMKV")
 val showMediaInfo=if(isReal)mediaInfoColumns.isNotEmpty() else true
 val castAllFocus=remember{FocusRequester()};val similarAllFocus=remember{FocusRequester()};val seasonFocus=remember{FocusRequester()};val countFocus=remember{FocusRequester()};val allEpisodesFocus=remember{FocusRequester()};
 var target by remember{mutableIntStateOf(0)}; val current by remember{derivedStateOf{if(scroll.firstVisibleItemIndex==0)target else (scroll.firstVisibleItemIndex-1).coerceIn(tabs.indices)}}
 fun close(){if(previewMedia!=null){previewMedia=null;return};if(menu!=null){menu=null;return};if(!exiting){exiting=true;scope.launch{scroll.scrollToItem(0);DetailOrigin.returning=true;launch{snapshotFlow{expansion.value}.collect{DetailOrigin.returnProgress=(1f-it).coerceIn(0f,1f)}};expansion.animateTo(0f,returnMotion());DetailOrigin.returnProgress=1f;withFrameNanos{};DetailOrigin.returning=false;withFrameNanos{};onDismiss(restore)}}}
 val currentClose by rememberUpdatedState({close()})
 DisposableEffect(media){registerClose {currentClose()};onDispose{registerClose(null)}}
 fun go(i:Int){target=i;navigate{scroll.animateScrollToItem(i+1,-with(density){128.dp.roundToPx()});withFrameNanos{};when(tabs[i]){"剧集"->seasonFocus;"演职人员"->castAllFocus;"类似作品"->similarAllFocus;else->infoFocus}.requestFocus()}}
 BackHandler{close()};LaunchedEffect(media){DetailOrigin.returnProgress=0f;DetailOrigin.returning=false;expansion.animateTo(1f,coverTransition());ready=true;withFrameNanos{};play.requestFocus()}
 val canvasHeight=LocalCanvasHeight.current
 val geometryProgress=expansion.value; val p=geometryProgress.coerceIn(0f,1f); fun mix(a:Float,b:Float)=a+(b-a)*geometryProgress
 val g=geometryProgress
 val finalWidth=if(g<0f)origin.width*(1f+g)else mix(origin.width,1672f)
 val finalHeight=if(g<0f)origin.height*(1f+g)else mix(origin.height,canvasHeight.value)
 val finalLeft=if(g<0f)origin.left+(origin.width-finalWidth)/2f else mix(origin.left,0f)
 val finalTop=if(g<0f)origin.top+(origin.height-finalHeight)/2f else mix(origin.top,0f)
 val scrollProgress={if(scroll.firstVisibleItemIndex>0)1f else (scroll.firstVisibleItemScrollOffset/with(density){941.dp.toPx()}).coerceIn(0f,1f)}
 val castEntrance=if(series)25 else 10;val similarEntrance=castEntrance+8;val infoEntrance=similarEntrance+7
 val rowSpecs=buildList{add(FocusRowSpec("back",0));add(FocusRowSpec("actions",0));if(showEpisodes){add(FocusRowSpec("episodeActions",1));add(FocusRowSpec("episodes",1))};val castIdx=if(series)2 else 1;if(showCast){if(!isReal)add(FocusRowSpec("演职人员:actions",castIdx));add(FocusRowSpec("cast",castIdx))};val simIdx=if(series)3 else 2;if(showSimilar){if(!isReal)add(FocusRowSpec("类似作品:actions",simIdx));add(FocusRowSpec("similar",simIdx))};val infoIdx=if(series)4 else 3;if(showInfo)add(FocusRowSpec("info",infoIdx));if(showMediaInfo)add(FocusRowSpec("mediaInfo",infoIdx))}
 FocusRowsHost(scroll,rowSpecs,ready&&!exiting&&menu==null){
 PageEntranceScope("detail:${media.title}",enabled=ready,replayOnOpen=true){
 Box(Modifier.fillMaxSize().zIndex(100f).onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.Escape){close();true}else false}.onKeyEvent{if(it.type==KeyEventType.KeyDown&&(it.key==Key.DirectionUp||it.key==Key.DirectionDown))moveVertical(it.key)else false}.focusProperties{onExit={if(!exiting)cancelFocusChange()}}.focusGroup()){
  Box(Modifier.fillMaxSize().then(if(RenderPerformance.blur31)Modifier.hazeSource(topBarSource)else Modifier)){
  Box(Modifier.offset(finalLeft.dp,finalTop.dp).size(finalWidth.dp,finalHeight.dp).then(if(exiting)Modifier.border(3.dp,White,ContinuousCornerShape((12f*(1f-p*p*p)).dp))else Modifier).clip(ContinuousCornerShape((12f*(1f-p*p*p)).dp)).then(if(RenderPerformance.blur33)Modifier.hazeSource(glassSource)else Modifier)){
   Box(Modifier.fillMaxSize().then(if(RenderPerformance.blur33)Modifier.hazeSource(wallpaperSource)else Modifier)){
    val detailBackdrop = media.realItem?.backdropUrl
    val remoteBackdrop = if (!detailBackdrop.isNullOrBlank()) rememberPosterImage(detailBackdrop) else null
    if(remoteBackdrop!=null){Image(remoteBackdrop,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
    else if(media.realItem!=null){Box(Modifier.fillMaxSize().background(Color(0xFF141414)))}
    else{Image(if(media.title=="海岸线之外")artwork.background.asImageBitmap() else artwork.image(media),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
   }
   if(RenderPerformance.blur33){
    Box(Modifier.fillMaxSize().hazeEffect(wallpaperSource){
     blurEnabled=scrollProgress()>0f
     blurRadius=(50f*scrollProgress()*p).coerceAtLeast(.01f).dp
     backgroundColor=Color.Transparent;tints=emptyList();noiseFactor=0f;inputScale=HazeInputScale.Fixed(.4f)
    })
   }else if(RenderPerformance.staticBlur&&legacyGlass!=null){
    Image(legacyGlass!!,null,Modifier.fillMaxSize().graphicsLayer{alpha=scrollProgress()*p},contentScale=ContentScale.Crop)
   }
   Box(Modifier.fillMaxSize().graphicsLayer{alpha=p*.9f}.background(Brush.horizontalGradient(listOf(Color(0xC9101010),Color(0x45101010),Color.Transparent))))
   Box(Modifier.fillMaxSize().graphicsLayer{alpha=p}.background(Brush.verticalGradient(listOf(Color(0x66101010),Color(0x28101010),Color(0xF5101010)))))
  }
  Canvas(Modifier.fillMaxSize()){ drawRect(Color.Black.copy(alpha=.648f*scrollProgress()*p)) }
  if(ready||exiting){
   LazyColumn(state=scroll,modifier=Modifier.fillMaxSize().graphicsLayer{alpha=if(exiting)0f else 1f}){
    item(key="hero"){Box(Modifier.fillMaxWidth()){
     Column(Modifier.padding(start=DetailDesign.inset,top=144.dp,bottom=48.dp).width(760.dp)){
      Column{val eyebrow=if(isReal)when(real?.mediaType){"Series"->"剧集   SERIES";"Movie"->"电影   MOVIE";"Episode"->"剧集   EPISODE";else->""}else if(series)"原创剧集   ORIGINAL SERIES"else"精选电影   FEATURE FILM";if(eyebrow.isNotBlank())Text(eyebrow,Modifier.staggeredEntrance(1),color=Muted,fontSize=18.sp,letterSpacing=3.sp);Spacer(Modifier.height(16.dp));val logoBmp=if(realLogoUrl.isNotBlank())rememberPosterImage(realLogoUrl)else null;if(logoBmp!=null){Image(logoBmp,media.title,Modifier.staggeredEntrance(2).height(132.dp).wrapContentWidth(Alignment.Start),contentScale=ContentScale.Fit,alignment=Alignment.CenterStart)}else{Text(media.title,Modifier.staggeredEntrance(2),color=White,fontSize=88.sp,lineHeight=112.sp,fontFamily=FontFamily.Default,maxLines=2,overflow=TextOverflow.Ellipsis)};if(realOriginalTitle.isNotBlank()||media.title=="海岸线之外")Text(if(media.title=="海岸线之外")"T H E   F A R   S I D E"else realOriginalTitle,Modifier.staggeredEntrance(3),color=Muted,fontSize=16.sp,letterSpacing=3.sp)}
      Spacer(Modifier.height(32.dp));StaggeredEntrance(4){val metaText=if(isReal)buildString{val yr=realYear;if(yr.isNotBlank())append(yr);val cnt=if(realEpisodes.isNotEmpty())realEpisodes.size else if(series)itemDetail?.recursiveItemCount?:0 else 0;if(cnt>0){if(isNotEmpty())append("   |   ");append("共 ");append(cnt);append(" 集")};if(realGenres.isNotEmpty()){if(isNotEmpty())append("   |   ");append(realGenres.joinToString(" / "))}}else if(series)(if(realEpisodes.isNotEmpty())(media.realItem?.year.orEmpty().ifBlank{"2024"}+"  |  共 "+realEpisodes.size+" 集")else "2024  |  共 12 集  |  剧情 · 家庭")else(media.detail+"  |  剧情");val badges=if(isReal)listOfNotNull(itemDetail?.officialRating?.takeIf{it.isNotBlank()},itemDetail?.communityRating?.takeIf{it.isNotBlank()}?.let{"★ "+it})else listOf("4K","HDR");Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically){if(metaText.isNotBlank())Text(metaText,color=White,fontSize=20.sp);badges.forEach{Text(it,Modifier.border(1.dp,Muted,ContinuousCornerShape(6.dp)).padding(8.dp),color=White,fontSize=18.sp)}}}
      val overviewText=if(isReal)realOverview else if(media.title=="海岸线之外")"有些相遇，从海的那一边开始。\n当熟悉的生活被潮水推远，她在海岸线之外，\n找回了自己，也看见了更大的世界。"else(media.title+"，一段关于相遇与成长的故事。\n在熟悉的世界之外，探索新的生活与可能。");if(overviewText.isNotBlank()){Spacer(Modifier.height(32.dp));StaggeredEntrance(5){Text(overviewText,color=Muted,fontSize=20.sp,lineHeight=32.sp)}}
      val hasProgress=!isReal||media.progress>0f||(real?.playbackPositionMs?:0L)>0L;if(hasProgress){Spacer(Modifier.height(32.dp));StaggeredEntrance(6){Row(horizontalArrangement=Arrangement.spacedBy(24.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.width(480.dp).height(8.dp).clip(Glass).background(Color(0x50888888))){Box(Modifier.fillMaxHeight().fillMaxWidth(if(isReal)media.progress.coerceIn(0f,1f) else media.progress.coerceAtLeast(.35f)).background(Color(0xFFFFFFFF)))};Text(formatMediaProgressText(media),color=Muted,fontSize=16.sp)}}};Spacer(Modifier.height(32.dp));Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){
       DPlayButton("▶  继续播放",Modifier.staggeredEntrance(7).size(264.dp,76.dp).rowFocusTarget(row="actions").focusRequester(play).focusProperties{up=back}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0);withFrameNanos{};back.requestFocus()};true}else if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionDown){go(0);true}else false}){val r=media.realItem;if(r!=null&&onPlayItem!=null){val firstEp=if(r.mediaType=="Series")realEpisodes.firstOrNull{it.streamUrl.isNotBlank()}else null;if(firstEp!=null){onPlayItem(MediaItemInfo(id=firstEp.id,accountId=r.accountId,serverType=r.serverType,title=firstEp.title,detail=firstEp.durationText,overview=firstEp.overview,posterUrl=firstEp.thumbUrl,backdropUrl=r.backdropUrl,streamUrl=firstEp.streamUrl,playbackPositionMs=firstEp.playbackPositionMs,totalDurationMs=firstEp.durationMs,mediaType="Episode",seasonNumber=firstEp.seasonNumber,episodeNumber=firstEp.episodeNumber,seriesId=r.seriesId))}else if(r.mediaType=="Series"){scope.launch{val pl=withContext(Dispatchers.IO){MediaLibraryManager.playableItem(r)};if(pl.streamUrl.isNotBlank())onPlayItem(pl)else menu="该剧集暂无可播放视频"}}else if(r.streamUrl.isNotBlank()){onPlayItem(r)}else{menu="播放演示"}}else{menu="播放演示"}}
       DButton(if(favorite)"♥ 已收藏"else"♡ 收藏",Modifier.staggeredEntrance(8).size(176.dp,76.dp).rowFocusTarget(row="actions").focusProperties{up=back;down=if(series)seasonFocus else castAllFocus}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0);withFrameNanos{};back.requestFocus()};true}else false},zoomOnFocus=false,glassSource=glassSource,legacyGlass=legacyGlass){favorite=!favorite;prefs.edit().putBoolean(media.title,favorite).apply();media.realItem?.let{MediaLibraryManager.toggleFavorite(it,favorite)}};DButton("··· 更多",Modifier.staggeredEntrance(9).size(176.dp,76.dp).rowFocusTarget(row="actions").focusProperties{up=back;down=if(series)seasonFocus else castAllFocus}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0);withFrameNanos{};back.requestFocus()};true}else false},zoomOnFocus=false,glassSource=glassSource,legacyGlass=legacyGlass){menu="更多操作"}
      }
     }

    }}
    if(series)item(key="episodes"){if(showEpisodes)DSection{
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){if(!isReal||seasons.size>1)DButton("第 $season 季",Modifier.staggeredEntrance(10).size(136.dp,52.dp).rowFocusTarget(row="episodeActions").focusRequester(seasonFocus).focusProperties{right=countFocus;down=shelf}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0);withFrameNanos{};play.requestFocus()};true}else false}){menu="选择季数"};DButton(if(realEpisodes.isNotEmpty())"共 ${realEpisodes.size} 集 ⌄" else "共 12 集 ⌄",Modifier.staggeredEntrance(11).size(184.dp,52.dp).rowFocusTarget(row="episodeActions").focusRequester(countFocus).focusProperties{left=seasonFocus;right=allEpisodesFocus;down=shelf}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0);withFrameNanos{};play.requestFocus()};true}else false}){menu="全部剧集"}};CapsuleTextAction("全部剧集 →",Modifier.staggeredEntrance(12).rowFocusTarget(row="episodeActions").focusRequester(allEpisodesFocus).focusProperties{left=countFocus;down=shelf}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0);withFrameNanos{};play.requestFocus()};true}else false},fontSize=20.sp){menu="全部剧集"}}
     Spacer(Modifier.height(DetailDesign.titleGap-DetailDesign.shelfPad));
     val epCount = if(realEpisodes.isNotEmpty()) realEpisodes.size else 12
     DShelf(epCount,shelf,368.dp,row="episodes",entranceIndex=13,onDown={go(tabs.indexOf("演职人员"))}){i,m->
      if(realEpisodes.isNotEmpty() && i < realEpisodes.size){
       val ep = realEpisodes[i]
       val epBmp = if(ep.thumbUrl.isNotBlank()) rememberPosterImage(ep.thumbUrl) else null
       DItem(m.focusProperties{up=seasonFocus},ep.title,ep.durationText,207.dp,epBmp ?: artwork.background.asImageBitmap(),ep.overview.ifBlank{null}){
        if(onPlayItem != null && media.realItem != null){
         val epItem = MediaItemInfo(id=ep.id, accountId=media.realItem.accountId, serverType=media.realItem.serverType, title=ep.title, detail=ep.durationText, overview=ep.overview, posterUrl=ep.thumbUrl, backdropUrl=media.realItem.backdropUrl, streamUrl=ep.streamUrl, playbackPositionMs=ep.playbackPositionMs, totalDurationMs=ep.durationMs)
         onPlayItem(epItem)
        } else { menu="播放第 ${i+1} 集" }
       }
      } else {
       DItem(m.focusProperties{up=seasonFocus},"S0$season E${(i+1).toString().padStart(2,'0')}  ${listOf("潮声之初","逆风而行","暮色灯塔","海的另一面","漂流的岛","无人知晓","再见，海岸","更大的世界")[i%8]}","${43+i%6} 分钟 · 演示剧集",207.dp,if(i%3==2)artwork.background.asImageBitmap()else artwork.image(DemoLibrary.watching[i%5]),"在海岸的风与潮声中，故事继续。"){menu="播放第 ${i+1} 集"}
      }
     }
    }}
    item(key="cast"){if(showCast)DSection{DSectionTitle("演职人员",entranceIndex=castEntrance,hasShelf=true,allModifier=Modifier.focusRequester(castAllFocus).focusProperties{down=if(series)castFocus else shelf},onAll=if(isReal)null else ({menu="演职人员"}));if(isReal){DShelf(castPeople.size,if(!series)shelf else castFocus,200.dp,row="cast",entranceIndex=castEntrance+1,onDown={go(tabs.indexOf("类似作品"))}){i,m->val p=castPeople[i];val bmp=if(p.imageUrl.isNotBlank())rememberPosterImage(p.imageUrl)else null;DItem(m.focusProperties{up=castAllFocus},p.name,p.role.ifBlank{when(p.type){"Director"->"导演";"Writer"->"编剧";else->"演员"}},244.dp,bmp?:artwork.background.asImageBitmap()){menu=p.name}}}else{val cast=listOf("林夏" to R.drawable.cast_zhouyutong,"陈牧驰" to R.drawable.cast_jingboran,"周雨彤" to R.drawable.cast_zhouyutong,"张颂文" to R.drawable.cast_zhangli,"李庚希" to R.drawable.cast_yongmei,"黄轩" to R.drawable.cast_jingboran,"王砚辉" to R.drawable.cast_zhangli);DShelf(cast.size,if(!series)shelf else castFocus,200.dp,row="cast",entranceIndex=castEntrance+1,onDown={go(tabs.indexOf("类似作品"))}){i,m->DItem(m.focusProperties{up=castAllFocus},cast[i].first,if(i==3)"导演 · 演示资料"else"主演 · 演示资料",244.dp,castPhotos.getOrElse(i){artwork.background.asImageBitmap()}){menu=cast[i].first}}}}}
    item(key="similar"){if(showSimilar)DSection{DSectionTitle("类似作品",entranceIndex=similarEntrance,hasShelf=true,allModifier=Modifier.focusRequester(similarAllFocus).focusProperties{down=similarFocus},onAll=if(isReal)null else ({menu="类似作品"}));if(isReal){DShelf(similarItems.size,similarFocus,LibraryDesign.posterWidth,row="similar",entranceIndex=similarEntrance+1,onDown={go(tabs.indexOf("更多信息"))}){i,m->val s=similarItems[i];val bmp=if(s.posterUrl.isNotBlank())rememberPosterImage(s.posterUrl)else null;DItem(m.focusProperties{up=similarAllFocus},s.title,s.detail,342.dp,bmp?:artwork.background.asImageBitmap()){previewMedia=s}}}else{DShelf(6,similarFocus,LibraryDesign.posterWidth,row="similar",entranceIndex=similarEntrance+1,onDown={go(tabs.indexOf("更多信息"))}){i,m->val related=(DemoLibrary.watching+DemoLibrary.favorites)[i];DItem(m.focusProperties{up=similarAllFocus},related.title,related.detail,342.dp,artwork.image(related)){menu="${related.title} · 演示推荐"}}}}}
    item(key="info"){if(showInfo||showMediaInfo)DSection{
     if(showInfo){DSectionTitle("更多信息",entranceIndex=infoEntrance);Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){
      if(detailRows1.isNotEmpty())Info(Modifier.weight(1f).staggeredEntrance(infoEntrance+1).rowFocusTarget(row="info").focusRequester(infoFocus).focusProperties{down=mediaInfoFocus}.readingFocus(),detailRows1)
      if(detailRows2.isNotEmpty())Info(Modifier.weight(1f).staggeredEntrance(infoEntrance+2).rowFocusTarget(row="info").focusRequester(infoFocus).focusProperties{down=mediaInfoFocus}.readingFocus(),detailRows2)
     }}
     if(showMediaInfo){if(showInfo)Spacer(Modifier.height(32.dp));DSectionTitle(if(itemDetail?.mediaSource==null&&episodeWithSource!=null)("媒体信息 · 第 "+episodeWithSource.episodeNumber+" 集")else "媒体信息",entranceIndex=infoEntrance+3);Row(Modifier.fillMaxWidth().staggeredEntrance(infoEntrance+4).rowFocusTarget(mediaInfoFocus,"mediaInfo").focusRequester(mediaInfoFocus).focusProperties{up=infoFocus}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionDown){navigate{scroll.animateScrollBy(with(density){240.dp.toPx()})};true}else false}.readingFocus().clip(ContinuousCornerShape(16.dp)).background(Color(0x80292929)).padding(32.dp),horizontalArrangement=Arrangement.SpaceBetween){mediaInfoColumns.forEach{Text(it,color=Muted,fontSize=20.sp,lineHeight=30.sp)}}}
     Spacer(Modifier.height(40.dp))
    }}
   }
  }
  }
  if(ready||exiting){
   TopBarBackdrop(topBarSource){
    if(exiting)0f else if(scroll.firstVisibleItemIndex>0)1f
    else (scroll.firstVisibleItemScrollOffset/with(density){506.dp.toPx()}).coerceIn(0f,1f)
   }
   Row(Modifier.fillMaxWidth().staggeredEntrance(0).graphicsLayer{alpha=if(exiting)0f else 1f}.padding(horizontal=DetailDesign.inset).offset(y=34.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){DButton("‹",Modifier.size(63.dp).rowFocusTarget(back,"back").focusRequester(back).onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0)};true}else if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionDown){navigate{scroll.animateScrollToItem(0);withFrameNanos{};play.requestFocus()};true}else false},zoomOnFocus=false){close()};var time by remember{mutableStateOf("")};LaunchedEffect(Unit){while(true){time=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date());delay(1000)}};Text(time,color=White,fontSize=21.sp)}
   if(menu!=null)Overlay(menu!!,onClose={menu=null}){when(menu){
    "选择季数"->listOf(1,2).forEach{n->DButton("第 $n 季${if(n==2)"（演示）"else""}",Modifier.width(360.dp).height(60.dp)){season=n;menu=null};Spacer(Modifier.height(24.dp))}
    "全部剧集"->{Text("第 $season 季 · 12 集",color=Muted,fontSize=24.sp);DButton("前往剧集列表",Modifier.width(360.dp).height(60.dp)){menu=null;navigate{scroll.animateScrollToItem(1);shelf.requestFocus()}}}
    "更多操作"->listOf("从头播放","标记为已看","媒体资料").forEach{action->DButton(action,Modifier.width(360.dp).height(60.dp)){menu="$action · 演示"};Spacer(Modifier.height(24.dp))}
    else->Text("演示交互，真实播放和媒体资料将在后续接入。",color=Muted,fontSize=22.sp)
   }}
   if(previewMedia!=null)Overlay(previewMedia!!.title,onClose={previewMedia=null}){Text(previewMedia!!.overview.ifBlank{"暂无简介"},color=Muted,fontSize=22.sp,lineHeight=32.sp)}
  }
 }
}
}
}
@Composable private fun DButton(label:String,modifier:Modifier,zoomOnFocus:Boolean=true,
 glassSource:HazeState?=null,legacyGlass:ImageBitmap?=null,onClick:()->Unit){
 val canvasHeight=LocalCanvasHeight.current
 var position by remember{mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)}
 FocusCard(modifier,50.dp,onClick,zoomOnFocus=zoomOnFocus,
  borderBlendMode=if(glassSource!=null)BlendMode.Overlay else BlendMode.SrcOver){
  Box(Modifier.fillMaxSize().onGloballyPositioned{position=it.boundsInRoot().topLeft}
   .then(if(glassSource!=null&&RenderPerformance.blur33)Modifier.hazeEffect(glassSource){
    blurRadius=24.dp;noiseFactor=0f;backgroundColor=Color(0xFF333333);inputScale=HazeInputScale.Fixed(.4f)
    tints=listOf(HazeTint(Color(0x40333333)))
   }else Modifier.background(Color(if(glassSource!=null)0x66333333 else 0xAA333333)))){
   if(glassSource!=null&&RenderPerformance.staticBlur&&legacyGlass!=null)Canvas(Modifier.fillMaxSize()){
    drawCoverWallpaper(legacyGlass,IntSize(1672.dp.roundToPx(),canvasHeight.roundToPx()),IntOffset(-position.x.toInt(),-position.y.toInt()))
    drawRect(Color(0x66333333))
   }
  }
  Text(label,Modifier.align(Alignment.Center),color=White,fontSize=if(label=="‹")51.sp else 20.sp)
 }
}
private fun Modifier.readingFocus():Modifier=composed{
 var focused by remember{mutableStateOf(false)}
 val amount by animateFloatAsState(if(focused)1f else 0f,focusMotion(),label="reading-focus")
 this.onFocusChanged{focused=it.isFocused}.focusable().whiteFocusGlow(amount,16.dp).focusSweep(focused,ContinuousCornerShape(16.dp))
  .border(2.dp,Color.White.copy(alpha=.7f*amount),ContinuousCornerShape(16.dp))
}
@Composable
private fun DPlayButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    var highlighted by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(if (highlighted) 1f else 0f, focusMotion(), label = "detail-play-highlight")
    FocusCard(modifier, 50.dp, onClick, zoomOnFocus = false, onHighlightChanged = { highlighted = it }, borderBlendMode = BlendMode.Overlay, borderOnlyWhenHighlighted = true) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
            lerp(Color(0x70484848), Color.White, reveal),
            lerp(Color(0x70303030), Color(0xFFD0D0D0), reveal)))))
        Text(label, Modifier.align(Alignment.Center), color = lerp(White, Color(0xFF161616), reveal), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun DSection(content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().padding(start=DetailDesign.inset,end=DetailDesign.inset,top=DetailDesign.sectionGap),content=content)}
@Composable private fun DSectionTitle(title:String,entranceIndex:Int,hasShelf:Boolean=false,allModifier:Modifier=Modifier,onAll:(()->Unit)?=null){Row(Modifier.fillMaxWidth().staggeredEntrance(entranceIndex).height(36.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(title,color=White,fontSize=LibraryDesign.heading,lineHeight=36.sp,fontWeight=FontWeight.Medium);if(onAll!=null)CapsuleTextAction("查看更多  →",allModifier.rowFocusTarget(row="${title}:actions"),onClick=onAll)};Spacer(Modifier.height(DetailDesign.titleGap-if(hasShelf)DetailDesign.shelfPad else 0.dp))}
@Composable private fun Info(modifier:Modifier,rows:List<Pair<String,String>>){Column(modifier.heightIn(min=420.dp).clip(ContinuousCornerShape(16.dp)).background(Color(0x80292929)).border(1.dp,Color(0x40808080),ContinuousCornerShape(16.dp)).padding(32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){rows.forEach{(k,v)->Row{Text(k,Modifier.width(176.dp),color=Muted,fontSize=20.sp);Text(v,color=White,fontSize=20.sp,lineHeight=30.sp)}}}}
@Composable private fun DShelf(count:Int,first:FocusRequester?,width:Dp,row:String?=null,entranceIndex:Int,onDown:(()->Unit)?=null,item: @Composable (Int, Modifier) -> Unit){
 val state=rememberLazyListState();val scope=rememberCoroutineScope();val d=LocalDensity.current;val refs=remember(count){List(count){i->if(i==0&&first!=null)first else FocusRequester()}}
 RegisterRowStart(row){state.animateScrollToItem(0)}
 val shelf=remember(state,scope,width,d){ShelfScrollController(state,scope,itemWidthPx={with(d){width.toPx()}},gapPx={with(d){DetailDesign.cardGap.toPx()}})}
 LazyRow(state=state,contentPadding=PaddingValues(horizontal=DetailDesign.inset,vertical=DetailDesign.shelfPad),horizontalArrangement=Arrangement.spacedBy(DetailDesign.cardGap),modifier=Modifier.wrapContentWidth(Alignment.Start,unbounded=true).requiredWidth(1672.dp).offset(x=-DetailDesign.inset).pointerInput(Unit){awaitPointerEventScope{while(true){val e=awaitPointerEvent(PointerEventPass.Initial);if(e.type==PointerEventType.Scroll){val delta=e.changes.firstOrNull()?.scrollDelta;if(delta!=null){scope.launch{state.animateScrollBy((if(delta.x!=0f)delta.x else delta.y)*with(d){72.dp.toPx()})};e.changes.forEach{it.consume()}}}}}}){items(count){i->item(i,Modifier.width(width).rowFocusTarget(refs[i],row).staggeredEntrance(entranceIndex+i).focusRequester(refs[i]).onPreviewKeyEvent{e->if(e.type==KeyEventType.KeyDown&&e.key==Key.DirectionDown&&onDown!=null){onDown();true}else if(e.type==KeyEventType.KeyDown&&(e.key==Key.DirectionLeft||e.key==Key.DirectionRight)){val t=(i+if(e.key==Key.DirectionRight)1 else -1).coerceIn(0,count-1);if(t!=i)shelf.select(t){refs[t].requestFocus()};true}else false})}}
}
@Composable private fun DItem(modifier:Modifier,title:String,metadata:String,height:Dp,image:ImageBitmap,description:String?=null,onClick:()->Unit){var focus by remember{mutableStateOf(false)};val zoom by animateFloatAsState(if(focus)1.1f else 1f,focusScaleMotion(focus),label="detail-focus");val bring=remember{BringIntoViewRequester()};var size by remember{mutableStateOf(IntSize.Zero)};val d=LocalDensity.current;LaunchedEffect(focus,size){if(focus&&size.height>0){withFrameNanos{};bring.bringIntoView(Rect(-size.width*.05f,-size.height*.05f-with(d){128.dp.toPx()},size.width*1.05f,size.height*1.05f+with(d){24.dp.toPx()}))}};Column(modifier.bringIntoViewRequester(bring).onSizeChanged{size=it}.zIndex(if(focus)10f else if(zoom>1.001f)5f else 0f).graphicsLayer{scaleX=zoom;scaleY=zoom}){FocusCard(Modifier.fillMaxWidth().height(height),onClick=onClick,zoomOnFocus=false,onHighlightChanged={focus=it}){Image(image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)};Spacer(Modifier.height(12.dp));Text(title,color=White,fontSize=24.sp,lineHeight=32.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text(metadata,color=Muted,fontSize=20.sp,lineHeight=28.sp,maxLines=1,overflow=TextOverflow.Ellipsis);if(description!=null)Text(description,color=Muted,fontSize=18.sp,lineHeight=28.sp,maxLines=2)}}
internal fun formatRuntimeText(ms:Long):String{val totalMin=ms/60000;val h=totalMin/60;val m=totalMin%60;return if(h>0)(if(m>0)(h.toString()+" 小时 "+m.toString()+" 分钟")else(h.toString()+" 小时"))else(m.toString()+" 分钟")}
private fun formatBytes(bytes:Long):String{if(bytes<=0)return "";val gb=bytes/1024.0/1024.0/1024.0;return if(gb>=1.0)((kotlin.math.round(gb*10)/10.0).toString()+" GB")else((kotlin.math.round(bytes/1024.0/1024.0)).toInt().toString()+" MB")}
