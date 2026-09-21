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
internal fun isDetailMedia(m:DemoMedia)=m !in DemoLibrary.music && m !in DemoLibrary.memories && m !in DemoLibrary.categories
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
 LaunchedEffect(media){if(Build.VERSION.SDK_INT<33)legacyGlass=withContext(Dispatchers.Default){artwork.blurredWallpaper(media)}}

 val castPhotos by produceState(emptyList<ImageBitmap>(),artwork){value=withContext(Dispatchers.Default){listOf(R.drawable.cast_zhouyutong,R.drawable.cast_jingboran,R.drawable.cast_zhouyutong,R.drawable.cast_zhangli,R.drawable.cast_yongmei,R.drawable.cast_jingboran,R.drawable.cast_zhangli).map{artwork.castImage(it)}}}
 val series=media.detail.contains("集")||media.detail.contains("电视剧")||media.title in listOf("暗涌","长安月","葬送的芙莉莲","我们的星球","地球脉动 III")
 val tabs=if(series)listOf("剧集","演职人员","类似作品","更多信息")else listOf("演职人员","类似作品","更多信息")
 val castAllFocus=remember{FocusRequester()};val similarAllFocus=remember{FocusRequester()};val seasonFocus=remember{FocusRequester()};val countFocus=remember{FocusRequester()};val allEpisodesFocus=remember{FocusRequester()};
 var target by remember{mutableIntStateOf(0)}; val current by remember{derivedStateOf{if(scroll.firstVisibleItemIndex==0)target else (scroll.firstVisibleItemIndex-1).coerceIn(tabs.indices)}}
 fun close(){if(menu!=null){menu=null;return};if(!exiting){exiting=true;scope.launch{scroll.scrollToItem(0);DetailOrigin.returning=true;launch{snapshotFlow{expansion.value}.collect{DetailOrigin.returnProgress=(1f-it).coerceIn(0f,1f)}};expansion.animateTo(0f,returnMotion());DetailOrigin.returnProgress=1f;DetailOrigin.returning=false;onDismiss(restore)}}}
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
 val rowSpecs=listOf(FocusRowSpec("back",0),FocusRowSpec("actions",0))+(if(series)listOf(FocusRowSpec("episodeActions",1),FocusRowSpec("episodes",1))else emptyList())+listOf(FocusRowSpec("演职人员:actions",if(series)2 else 1),FocusRowSpec("cast",if(series)2 else 1),FocusRowSpec("类似作品:actions",if(series)3 else 2),FocusRowSpec("similar",if(series)3 else 2),FocusRowSpec("info",if(series)4 else 3),FocusRowSpec("mediaInfo",if(series)4 else 3))
 FocusRowsHost(scroll,rowSpecs,ready&&!exiting&&menu==null){
 PageEntranceScope("detail:${media.title}",enabled=ready,replayOnOpen=true){
 Box(Modifier.fillMaxSize().zIndex(100f).onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.Escape){close();true}else false}.onKeyEvent{if(it.type==KeyEventType.KeyDown&&(it.key==Key.DirectionUp||it.key==Key.DirectionDown))moveVertical(it.key)else false}.focusProperties{onExit={if(!exiting)cancelFocusChange()}}.focusGroup()){
  Box(Modifier.fillMaxSize().then(if(Build.VERSION.SDK_INT>=31)Modifier.hazeSource(topBarSource)else Modifier)){
  Box(Modifier.offset(finalLeft.dp,finalTop.dp).size(finalWidth.dp,finalHeight.dp).then(if(exiting)Modifier.border(3.dp,White,ContinuousCornerShape((12f*(1f-p*p*p)).dp))else Modifier).clip(ContinuousCornerShape((12f*(1f-p*p*p)).dp)).then(if(Build.VERSION.SDK_INT>=33)Modifier.hazeSource(glassSource)else Modifier)){
   Box(Modifier.fillMaxSize().then(if(Build.VERSION.SDK_INT>=33)Modifier.hazeSource(wallpaperSource)else Modifier)){
    Image(artwork.image(media),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    if(media.title=="海岸线之外")Image(artwork.background.asImageBitmap(),null,Modifier.fillMaxSize().graphicsLayer{alpha=p},contentScale=ContentScale.Crop)
   }
   if(Build.VERSION.SDK_INT>=33){
    Box(Modifier.fillMaxSize().hazeEffect(wallpaperSource){
     blurEnabled=scrollProgress()>0f
     blurRadius=(50f*scrollProgress()*p).coerceAtLeast(.01f).dp
     backgroundColor=Color.Transparent;tints=emptyList();noiseFactor=0f;inputScale=HazeInputScale.Fixed(.5f)
    })
   }else if(legacyGlass!=null){
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
      Column{Text(if(series)"原创剧集   ORIGINAL SERIES"else"精选电影   FEATURE FILM",Modifier.staggeredEntrance(1),color=Muted,fontSize=18.sp,letterSpacing=3.sp);Spacer(Modifier.height(16.dp));Text(media.title,Modifier.staggeredEntrance(2),color=White,fontSize=88.sp,lineHeight=112.sp,fontFamily=if(media.title=="海岸线之外")FontFamily(Font(R.font.ma_shan_zheng))else FontFamily.Default,maxLines=2,overflow=TextOverflow.Ellipsis);Text(if(media.title=="海岸线之外")"T H E   F A R   S I D E"else"精选影视",Modifier.staggeredEntrance(3),color=Muted,fontSize=16.sp,letterSpacing=3.sp)}
      Spacer(Modifier.height(32.dp));StaggeredEntrance(4){Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically){Text(if(series)"2024  |  共 12 集  |  剧情 · 家庭"else"${media.detail}  |  剧情",color=White,fontSize=20.sp);listOf("4K","HDR").forEach{Text(it,Modifier.border(1.dp,Muted,ContinuousCornerShape(6.dp)).padding(8.dp),color=White,fontSize=18.sp)}}}
      Spacer(Modifier.height(32.dp));StaggeredEntrance(5){Text(if(media.realItem?.overview?.isNotBlank()==true)media.realItem.overview else if(media.title=="海岸线之外")"有些相遇，从海的那一边开始。\n当熟悉的生活被潮水推远，她在海岸线之外，\n找回了自己，也看见了更大的世界。"else"${media.title}，一段关于相遇与成长的故事。\n在熟悉的世界之外，探索新的生活与可能。\n当前简介为演示内容，后续将接入真实媒体信息。",color=Muted,fontSize=20.sp,lineHeight=32.sp)}
      Spacer(Modifier.height(32.dp));StaggeredEntrance(6){Row(horizontalArrangement=Arrangement.spacedBy(24.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.width(480.dp).height(8.dp).clip(Glass).background(Color(0x50888888))){Box(Modifier.fillMaxHeight().fillMaxWidth(media.progress.coerceAtLeast(.35f)).background(Color(0xFFFFFFFF)))};Text("01:24:36 / 02:35:00",color=Muted,fontSize=16.sp)}};Spacer(Modifier.height(32.dp));Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){
       DPlayButton("▶  继续播放",Modifier.staggeredEntrance(7).size(264.dp,76.dp).rowFocusTarget(row="actions").focusRequester(play).focusProperties{up=back}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0);withFrameNanos{};back.requestFocus()};true}else if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionDown){go(0);true}else false}){if(media.realItem?.streamUrl?.isNotBlank()==true&&onPlayItem!=null){onPlayItem(media.realItem)}else{menu="播放演示"}}
       DButton(if(favorite)"♥ 已收藏"else"♡ 收藏",Modifier.staggeredEntrance(8).size(176.dp,76.dp).rowFocusTarget(row="actions").focusProperties{up=back;down=if(series)seasonFocus else castAllFocus}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0);withFrameNanos{};back.requestFocus()};true}else false},zoomOnFocus=false,glassSource=glassSource,legacyGlass=legacyGlass){favorite=!favorite;prefs.edit().putBoolean(media.title,favorite).apply()};DButton("··· 更多",Modifier.staggeredEntrance(9).size(176.dp,76.dp).rowFocusTarget(row="actions").focusProperties{up=back;down=if(series)seasonFocus else castAllFocus}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0);withFrameNanos{};back.requestFocus()};true}else false},zoomOnFocus=false,glassSource=glassSource,legacyGlass=legacyGlass){menu="更多操作"}
      }
     }

    }}
    if(series)item(key="episodes"){DSection{
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){DButton("第 $season 季",Modifier.staggeredEntrance(10).size(136.dp,52.dp).rowFocusTarget(row="episodeActions").focusRequester(seasonFocus).focusProperties{right=countFocus;down=shelf}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0);withFrameNanos{};play.requestFocus()};true}else false}){menu="选择季数"};DButton("共 12 集 ⌄",Modifier.staggeredEntrance(11).size(184.dp,52.dp).rowFocusTarget(row="episodeActions").focusRequester(countFocus).focusProperties{left=seasonFocus;right=allEpisodesFocus;down=shelf}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0);withFrameNanos{};play.requestFocus()};true}else false}){menu="全部剧集"}};CapsuleTextAction("全部剧集 →",Modifier.staggeredEntrance(12).rowFocusTarget(row="episodeActions").focusRequester(allEpisodesFocus).focusProperties{left=countFocus;down=shelf}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionUp){navigate{scroll.animateScrollToItem(0);withFrameNanos{};play.requestFocus()};true}else false},fontSize=20.sp){menu="全部剧集"}}
     Spacer(Modifier.height(DetailDesign.titleGap-DetailDesign.shelfPad));DShelf(12,shelf,368.dp,row="episodes",entranceIndex=13,onDown={go(tabs.indexOf("演职人员"))}){i,m->DItem(m.focusProperties{up=seasonFocus},"S0$season E${(i+1).toString().padStart(2,'0')}  ${listOf("潮声之初","逆风而行","暮色灯塔","海的另一面","漂流的岛","无人知晓","再见，海岸","更大的世界")[i%8]}","${43+i%6} 分钟 · 演示剧集",207.dp,if(i%3==2)artwork.background.asImageBitmap()else artwork.image(DemoLibrary.watching[i%5]),"在海岸的风与潮声中，故事继续。"){menu="播放第 ${i+1} 集"}}
    }}
    item(key="cast"){DSection{DSectionTitle("演职人员",entranceIndex=castEntrance,hasShelf=true,allModifier=Modifier.focusRequester(castAllFocus).focusProperties{down=if(series)castFocus else shelf},onAll={menu="演职人员"});val cast=listOf("林夏" to R.drawable.cast_zhouyutong,"陈牧驰" to R.drawable.cast_jingboran,"周雨彤" to R.drawable.cast_zhouyutong,"张颂文" to R.drawable.cast_zhangli,"李庚希" to R.drawable.cast_yongmei,"黄轩" to R.drawable.cast_jingboran,"王砚辉" to R.drawable.cast_zhangli);DShelf(cast.size,if(!series)shelf else castFocus,200.dp,row="cast",entranceIndex=castEntrance+1,onDown={go(tabs.indexOf("类似作品"))}){i,m->DItem(m.focusProperties{up=castAllFocus},cast[i].first,if(i==3)"导演 · 演示资料"else"主演 · 演示资料",244.dp,castPhotos.getOrElse(i){artwork.background.asImageBitmap()}){menu=cast[i].first}}}}
    item(key="similar"){DSection{DSectionTitle("类似作品",entranceIndex=similarEntrance,hasShelf=true,allModifier=Modifier.focusRequester(similarAllFocus).focusProperties{down=similarFocus},onAll={menu="类似作品"});DShelf(6,similarFocus,LibraryDesign.posterWidth,row="similar",entranceIndex=similarEntrance+1,onDown={go(tabs.indexOf("更多信息"))}){i,m->val related=(DemoLibrary.watching+DemoLibrary.favorites)[i];DItem(m.focusProperties{up=similarAllFocus},related.title,related.detail,342.dp,artwork.image(related)){menu="${related.title} · 演示推荐"}}}}
    item(key="info"){DSection{DSectionTitle("更多信息",entranceIndex=infoEntrance);Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){
     Info(Modifier.weight(1f).staggeredEntrance(infoEntrance+1).rowFocusTarget(row="info").focusRequester(infoFocus).focusProperties{down=mediaInfoFocus}.readingFocus(),listOf("原始标题" to media.title,"首播时间" to "2024 年 3 月 15 日","集数 / 时长" to if(series)"12 集（第 $season 季）"else"155 分钟","类型" to if(series)"剧情 / 家庭"else"电影 / 剧情","国家 / 地区" to "中国","语言" to "中文","标签" to "成长 / 海岸 / 生活"))
     Info(Modifier.weight(1f).staggeredEntrance(infoEntrance+2).rowFocusTarget(row="info").focusProperties{down=mediaInfoFocus}.readingFocus(),listOf("导演" to "演示资料","编剧" to "演示资料","主演" to "林夏、陈牧驰、周雨彤","简介" to "一段关于相遇与成长的故事。\n这些信息仅用于页面展示，后续将替换为真实媒体资料。"))
    };Spacer(Modifier.height(32.dp));DSectionTitle("媒体信息",entranceIndex=infoEntrance+3);Row(Modifier.fillMaxWidth().staggeredEntrance(infoEntrance+4).rowFocusTarget(mediaInfoFocus,"mediaInfo").focusRequester(mediaInfoFocus).focusProperties{up=infoFocus}.onPreviewKeyEvent{if(it.type==KeyEventType.KeyDown&&it.key==Key.DirectionDown){navigate{scroll.animateScrollBy(with(density){240.dp.toPx()})};true}else false}.readingFocus().clip(ContinuousCornerShape(16.dp)).background(Color(0x80292929)).padding(32.dp),horizontalArrangement=Arrangement.SpaceBetween){listOf("视频\n4K · HEVC\n3840 × 2160","音频\nDolby Atmos\n中文 5.1","字幕\n简体中文 / English\nSRT · 内嵌","文件大小\n约 1.2 GB / 集\nMKV").forEach{Text(it,color=Muted,fontSize=20.sp,lineHeight=30.sp)}};Spacer(Modifier.height(40.dp))}}
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
   .then(if(glassSource!=null&&Build.VERSION.SDK_INT>=33)Modifier.hazeEffect(glassSource){
    blurRadius=24.dp;noiseFactor=0f;backgroundColor=Color(0xFF333333)
    tints=listOf(HazeTint(Color(0x40333333)))
   }else Modifier.background(Color(if(glassSource!=null)0x66333333 else 0xAA333333)))){
   if(glassSource!=null&&Build.VERSION.SDK_INT<33&&legacyGlass!=null)Canvas(Modifier.fillMaxSize()){
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
    val reveal by animateFloatAsState(if (highlighted) 1f else 0f, tween(200), label = "detail-play-highlight")
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
