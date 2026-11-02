package com.qingtx.qingtiixing.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 隐私政策。
 *
 * 为什么单独一个文件：应用商店（华为、小米、OPPO、vivo、应用宝）审核的第一道关
 * 就是「启动时有没有弹隐私政策、同意之前有没有偷偷申请权限」。
 * 之前这版应用根本没有这一步，材料再齐全也过不了。
 *
 * 正文写成常量而不是网页：这个应用没有网络权限，如果只给一个网址，
 * 用户在飞机上、或者在没网的地方点开就是白屏，审核员也可能正好在无网环境里看。
 */

internal const val PRIVACY_URL = "https://fushengzhenjun.github.io/qing-tixing/privacy.html"
internal const val PRIVACY_DATE = "2026 年 11 月 1 日"

internal data class PrivacyBlock(val title: String, val body: String)

/** 权限说明单独列出来：商店表单里要逐条填「权限用途」，两处用同一份，改一处就够 */
internal data class PrivacyPerm(val name: String, val why: String, val note: String)

internal val PRIVACY_PERMS = listOf(
    PrivacyPerm(
        "通知",
        "到点时在本机弹出提醒。这是提醒能生效的前提。",
        "可以拒绝，拒绝后到点不会有任何提示。"
    ),
    PrivacyPerm(
        "精确闹钟",
        "按你设定的那一分钟准时响。不授予的话系统会把提醒往后挪，可能晚几分钟到几十分钟。",
        "可以拒绝，拒绝后提醒时间会不准。"
    ),
    PrivacyPerm(
        "开机自启",
        "手机重启后把还没到期的提醒重新登记一遍。不授予的话，重启之后原本的提醒就都不会响了。",
        "安装时自动获得，不需要你操作。"
    ),
    PrivacyPerm(
        "振动",
        "到点振动。",
        "安装时自动获得，可在应用设置里关掉。"
    ),
    PrivacyPerm(
        "忽略电池优化",
        "避免系统在后台把应用冻结，导致到点不响或迟响。",
        "需要你在系统弹窗里手动确认，可以拒绝。"
    ),
    PrivacyPerm(
        "全屏通知",
        "锁屏或别的应用开着时，把提醒页整个盖到最上层。Android 14 起需要你单独允许。",
        "可以拒绝，拒绝后只在通知栏提示。"
    ),
    PrivacyPerm(
        "自选图片（背景图）",
        "你在设置里主动挑一张图当背景时，通过系统文件选择器读取这一张图。",
        "不申请相册/存储权限，读不到你没选的任何文件。"
    )
)

internal val PRIVACY_BLOCKS = listOf(
    PrivacyBlock(
        "一、我们不收集任何信息",
        "轻铃是一款完全离线的到点提醒工具。应用没有申请网络权限（INTERNET），" +
            "在系统层面就无法建立任何网络连接——这一点可以在手机的「应用信息 → 权限」里直接核对，那里不会出现网络相关的条目。\n\n" +
            "因此我们不会、也无法收集、上传、存储、共享或出售你的任何个人信息。" +
            "没有账号体系，没有服务器，没有广告，没有第三方统计，没有崩溃上报，也没有设备标识采集。"
    ),
    PrivacyBlock(
        "二、你的数据存在哪里",
        "提醒内容、分类、设置项、每日自动备份，全部保存在本应用的私有目录中" +
            "（/data/data/com.qingtx.qingtiixing/）。这是 Android 的沙箱目录，其他应用无法读取。\n\n" +
            "这些数据不会写入公共存储空间，也不会同步到任何云端。" +
            "只有你主动点「导出」时，内容才会写到你当场选定的那个位置。"
    ),
    PrivacyBlock(
        "三、需要哪些权限，分别用来做什么",
        "见下方「权限说明」逐条列出。总的原则是：每一项都与「到点能响」直接相关，" +
            "没有一项是为了收集信息而申请的。除通知、精确闹钟、电池优化、全屏通知这四项需要你确认外，" +
            "其余在安装时由系统授予，不做额外索取。"
    ),
    PrivacyBlock(
        "四、我们不做的事",
        "· 不要求注册、登录，不收集姓名、手机号、邮箱、身份证等任何身份信息；\n" +
            "· 不读取通讯录、短信、通话记录、相册、位置、日历；\n" +
            "· 不采集 IMEI、MAC 地址、Android ID、序列号等设备标识；\n" +
            "· 不读取剪贴板，不写剪贴板（复制反馈信息除外，且只在你点击时写入本机剪贴板）；\n" +
            "· 不接入广告 SDK、统计 SDK、推送 SDK、社交分享 SDK；\n" +
            "· 不在后台发起任何网络请求；\n" +
            "· 不做用户画像，不做个性化推荐，不向第三方提供任何数据。"
    ),
    PrivacyBlock(
        "五、第三方组件",
        "应用不含任何第三方 SDK。运行时只依赖 Android 系统组件与 AndroidX / Kotlin 官方基础库，" +
            "这些库由系统或安装包静态链接提供，均不具备统计、广告或数据回传功能。"
    ),
    PrivacyBlock(
        "六、你主动导出的文件",
        "导出 JSON 备份或 .ics 日历时，文件会保存到你选择的位置（下载目录、网盘或其他应用）。" +
            "这一步完全由你发起，文件去向由你决定，我们不经手也不留存任何副本。\n\n" +
            "请注意：导出的文件里包含你的提醒标题与时间，如果把文件分享给别人，对方就能看到这些内容。"
    ),
    PrivacyBlock(
        "七、如何删除你的数据",
        "· 单条：在主界面左滑或长按删除；\n" +
            "· 全部：设置 → 数据 → 清空全部提醒（清空前会自动留一份备份，备份也在应用私有目录里）；\n" +
            "· 彻底：卸载应用，私有目录连同备份一并被系统删除。\n\n" +
            "因为我们从未收到过你的数据，所以不存在「需要联系我们删除服务器数据」这种情况。"
    ),
    PrivacyBlock(
        "八、未成年人",
        "应用不收集任何信息，对任何年龄的用户一视同仁。若未成年人在监护人指导下使用，" +
            "所产生的提醒内容同样只保存在本机。"
    ),
    PrivacyBlock(
        "九、政策变更",
        "政策如有修改，会同时更新应用内的这份正文和网页版，并修改上方日期。" +
            "涉及收集范围变化的重大修改，会在应用内重新征求你的同意，而不是默认生效。"
    ),
    PrivacyBlock(
        "十、联系我们",
        "开发者：fushengzhenjun（个人开发者）\n" +
            "任何疑问、投诉或数据相关问题，可通过项目仓库提交：\n" +
            "https://github.com/fushengzhenjun/qing-tixing/issues"
    )
)

/* ==================== 首次启动：同意页 ==================== */

/**
 * 首次启动的隐私政策页。
 *
 * 两个硬性要求，缺一个就会被驳回：
 *   1. 必须能读到完整条款，不能只有一句「我们重视你的隐私」；
 *   2. 必须有明确的「不同意」，且不同意时不得进入应用。
 *
 * 另外，在用户点「同意」之前，这个界面之外不允许有任何权限弹窗——
 * 所以通知权限的申请写在 AppRoot 里，而 AppRoot 只有在同意之后才会被组合出来。
 */
@Composable
fun ConsentGate(
    onAgree: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "铃",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("轻铃 · 隐私政策", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "更新于 $PRIVACY_DATE",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                "一句话说明：这个应用不联网、不收集任何信息，你的提醒只存在这台手机里。",
                fontSize = 13.5.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))

            PrivacyBody()

            Spacer(Modifier.height(8.dp))
            Text(
                "完整网页版：$PRIVACY_URL",
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                "点击「同意并继续」表示你已阅读并同意上述全部内容。\n" +
                    "不同意也不会影响你手机上已有的任何数据——本应用从不向外发送数据。",
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        "不同意",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onAgree,
                    modifier = Modifier
                        .weight(1.6f)
                        .height(48.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("同意并继续", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/* ==================== 设置里再次查看 ==================== */

/** 从「关于 → 隐私政策」进来。只读，带返回；底部留一个网页版入口方便转发给别人 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("隐私政策", fontWeight = FontWeight.SemiBold, fontSize = 19.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "更新于 $PRIVACY_DATE",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            PrivacyBody()
            Spacer(Modifier.height(22.dp))
            TextButton(
                onClick = {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
                    }
                }
            ) {
                Text("在浏览器中打开网页版", fontSize = 13.sp)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/* ==================== 正文 ==================== */

@Composable
private fun PrivacyBody() {
    PRIVACY_BLOCKS.forEachIndexed { i, b ->
        if (i > 0) Spacer(Modifier.height(18.dp))
        Text(b.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        if (b.title.startsWith("三、")) {
            PermissionList()
            Spacer(Modifier.height(8.dp))
        }
        Text(
            b.body,
            fontSize = 12.5.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionList() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        PRIVACY_PERMS.forEachIndexed { i, p ->
            if (i > 0) {
                Spacer(Modifier.height(9.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(9.dp))
            }
            Text(p.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                p.why,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                p.note,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            )
        }
    }
}
