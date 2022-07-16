package net.cassite.tdpcli.daemon

import io.vproxy.base.connection.NetEventLoop
import io.vproxy.base.selector.PeriodicEvent
import io.vproxy.base.selector.SelectorEventLoop
import io.vproxy.base.util.thread.VProxyThread
import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.lib.common.coroutine
import io.vproxy.lib.common.launch
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vfd.IPPort
import net.cassite.tdpcli.Args
import net.cassite.tdpcli.IntelPlatform
import net.cassite.tdpcli.Platform
import net.cassite.tdpcli.util.Utils
import net.cassite.tdpcli.util.Version

class Daemon(private val ipport: IPPort, private val platform: Platform, private val config: Config) {
  private var args: Args? = null
  private val loop = NetEventLoop(SelectorEventLoop.open())
  private lateinit var periodicEvent: PeriodicEvent
  private val server: CoroutineHttp1Server

  init {
    loop.selectorEventLoop.loop { VProxyThread.create(it, "daemon-thread") }

    val serverSock = io.vproxy.base.connection.ServerSock.create(ipport)
    server = CoroutineHttp1Server(serverSock.coroutine(loop))

    server.all("/", ::accessLog)
    server.all("/*", ::accessLog)
    server.get("/tdpcli/api/v1.0/version") { it.conn.response(200).send(ObjectBuilder().put("version", Version.VERSION).build()) }
    server.get("/tdpcli/api/v1.0/power_limit", ::getPowerLimit)
    server.get("/tdpcli/api/v1.0/config", ::getConfig)
    server.put("/tdpcli/api/v1.0/power_limit", ::setPowerLimit)
    server.put("/tdpcli/api/v1.0/config", ::setConfig)
  }

  fun start() {
    loop.selectorEventLoop.launch {
      Utils.info("daemon is listening on $ipport")
      server.start()
    }
  }

  private fun restartTimer() {
    if (::periodicEvent.isInitialized) {
      periodicEvent.cancel()
    }
    periodicEvent = loop.selectorEventLoop.period(config.interval * 1000) { intervalUpdate() }
    intervalUpdate() // execute now
  }

  private fun intervalUpdate() {
    Utils.debug("interval update enters")
    val args = this.args ?: return
    Utils.debug("interval update executes")
    platform.updatePowerLimit(args)
  }

  fun setArgs(args: Args) {
    Utils.info("power limit update: ${args.plFieldsToString()}")
    if (this.args == null) {
      this.args = args
    } else {
      this.args!!.from(args)
    }
    platform.updatePowerLimit(args)
    restartTimer()
  }

  private fun accessLog(ctx: RoutingContext) {
    if (ctx.req.body().length() == 0) {
      Utils.info("[access] ${ctx.req.method()} ${ctx.req.uri()}")
    } else {
      Utils.info("[access] ${ctx.req.method()} ${ctx.req.uri()} ${ctx.req.body()}")
    }
    ctx.allowNext()
  }

  private suspend fun getPowerLimit(ctx: RoutingContext) {
    val mode = ctx.req.query()["mode"]
    if (mode != null && mode != "" && mode != "msr" && mode != "mmio") {
      ctx.conn.response(400).send(ObjectBuilder().put("code", 400).put("message", "invalid mode").build())
      return
    }
    if (mode != null && mode != "") {
      if (platform !is IntelPlatform) {
        ctx.conn.response(400).send(ObjectBuilder().put("code", 400).put("message", "cannot use mode=$mode on current platform").build())
        return
      }
    }
    val res = when (mode) {
      "msr" -> (platform as IntelPlatform).msrPowerLimit
      "mmio" -> (platform as IntelPlatform).mmioPowerLimit
      else -> platform.powerLimit
    }
    ctx.conn.response(200).send(res.formatToJson())
  }

  private suspend fun setPowerLimit(ctx: RoutingContext) {
    val body = ctx.req.body().toString()
    val pl = JSON.deserialize(body, PowerLimitArgs.rule)
    var needToAssign = false
    val args = if (this.args == null) {
      needToAssign = true
      Args()
    } else this.args
    val err = pl.checkAndAssignToArgs(args)
    if (err != null) {
      ctx.conn.response(400).send(ObjectBuilder().put("code", 400).put("message", err).build())
      return
    }
    if (needToAssign) {
      this.args = args
    }
    restartTimer()
    ctx.conn.response(204).send()
  }

  private suspend fun getConfig(ctx: RoutingContext) {
    ctx.conn.response(200).send(config.toJson())
  }

  private suspend fun setConfig(ctx: RoutingContext) {
    val body = ctx.req.body().toString()
    val config = JSON.deserialize(body, Config.rule)
    if (config.interval != 0) {
      if (config.interval < 0) {
        ctx.conn.response(400).send(ObjectBuilder().put("code", 400).put("message", "cannot use negative interval").build())
        return
      }
      this.config.interval = config.interval
      restartTimer()
    }

    ctx.conn.response(204).send()
  }
}
