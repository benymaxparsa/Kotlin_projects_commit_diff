import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.mamoe.mirai.Bot
import net.mamoe.mirai.login
import net.mamoe.mirai.network.NetworkScope
import net.mamoe.mirai.network.protocol.tim.packet.login.LoginState
import net.mamoe.mirai.utils.BotAccount
import net.mamoe.mirai.utils.Console
import java.util.*

/**
 * 筛选掉无法登录(冻结/设备锁/UNKNOWN)的 qq
 *
 * @author Him188moe
 */

const val qqList = "" +
        "3383596103----13978930542\n" +
        "3342679146----aaaa9899\n" +
        "1491095272----abc123\n" +
        "3361065539----aaaa9899\n" +
        "1077612696----asd123456789\n" +
        "\n" +
        "\n" +
        "\n" +
        "\n" +
        "\n" +
        "\n" +
        "\n" +
        "\n" +
        "\n" +
        "\n"

suspend fun main() {
    val goodBotList = Collections.synchronizedList(mutableListOf<Bot>())

    withContext(NetworkScope.coroutineContext) {
        qqList.split("\n")
                .filterNot { it.isEmpty() }
                .map { it.split("----") }
                .map { Pair(it[0].toLong(), it[1]) }
                .forEach { (qq, password) ->
                    runBlocking {
                        val bot = Bot(
                                BotAccount(
                                        qq,
                                        if (password.endsWith(".")) password.substring(0, password.length - 1) else password
                                ),
                                Console()
                        )

                        withContext(Dispatchers.IO) {
                            bot.login()
                        }.let { state ->
                            if (state == LoginState.SUCCESS) {
                                goodBotList.add(bot)
                            }
                        }
                    }
                }
    }

    println("Filtering finished")
    println(goodBotList.joinToString("\n") { it.account.qqNumber.toString() + "    " + it.account.password })
}
