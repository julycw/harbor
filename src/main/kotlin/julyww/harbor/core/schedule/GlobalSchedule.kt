package julyww.harbor.core.schedule

import cn.trustway.nb.util.DateUtil
import julyww.harbor.core.application.AppManageService
import julyww.harbor.core.application.AppQueryBean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class GlobalSchedule(
    private val appManageService: AppManageService
) {

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    fun scheduleRestart() {
        val now = DateUtil.convert2String(Date(), DateUtil.CHN_TIME_FORMAT)
        val prevTime = DateUtil.convert2String(DateUtil.addMinutes(Date(), -10), DateUtil.CHN_TIME_FORMAT)
        val list = appManageService.list(
            AppQueryBean(
                filterByContainerExist = true,
                withRemoteMd5 = false
            )
        ).list.filter {
            it.scheduleRestart == true && !it.restartAt.isNullOrBlank()
        }

        for (appDTO in list) {
            if (now >= appDTO.restartAt!! && prevTime < appDTO.restartAt!!) {
                appManageService.restart(appDTO.id!!)
            }
        }
    }


    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    fun scheduleUpdate() {
        val now = DateUtil.convert2String(Date(), DateUtil.CHN_TIME_FORMAT)
        val prevTime = DateUtil.convert2String(DateUtil.addMinutes(Date(), -10), DateUtil.CHN_TIME_FORMAT)
        val list = appManageService.list(
            AppQueryBean(
                filterByContainerExist = true,
                withRemoteMd5 = false
            )
        ).list.filter {
            it.scheduleUpdate == true && !it.updateAt.isNullOrBlank()
        }

        for (appDTO in list) {
            if (now >= appDTO.updateAt!! && prevTime < appDTO.updateAt!!) {
                appManageService.update(appDTO.id!!)
            }
        }
    }

}