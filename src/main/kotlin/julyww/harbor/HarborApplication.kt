package julyww.harbor

import cn.trustway.nb.util.HashUtil
import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties
import de.codecentric.boot.admin.server.config.EnableAdminServer
import julyww.harbor.core.certification.CertificationDTO
import julyww.harbor.core.certification.CertificationService
import julyww.harbor.core.certification.persist.CertificationType
import julyww.harbor.persist.app.AppRepository
import julyww.harbor.props.HarborProps
import julyww.harbor.utils.Environments
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@EnableEncryptableProperties
@RestControllerAdvice
@EnableScheduling
@EnableFeignClients
@EnableAdminServer
@SpringBootApplication
class HarborApplication(
    private val appRepository: AppRepository,
    private val harborProps: HarborProps,
    private val environments: Environments,
    private val certificationService: CertificationService
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(HarborApplication::class.java)

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(
        value = [
            IllegalStateException::class,
            IllegalArgumentException::class
        ]
    )
    fun exceptionHandler(e: Exception): String? {
        return e.message
    }

    private fun init() {
        // 将app中的下载授权信息进行提取，统一迁移到授权模块中
        try {
            val appList = appRepository.findAll().filter {
                it.certificationId.isNullOrBlank() && !it.basicAuthUsername.isNullOrBlank() && !it.basicAuthPassword.isNullOrBlank()
            }

            val groups = appList.groupBy {
                HashUtil.encryptMD5("${it.basicAuthUsername}:${it.basicAuthPassword}")
            }

            groups.forEach { (_, items) ->
                val first = items.first()
                val cert = certificationService.save(CertificationDTO(
                    id = null,
                    name = first.basicAuthUsername,
                    type = CertificationType.UsernamePassword,
                    username = first.basicAuthUsername,
                    password = first.basicAuthPassword
                ))
                items.forEach { it.certificationId = cert.id }
                appRepository.saveAll(items)
            }

        } catch (ignore: Exception) {
        }
    }

    override fun run(vararg args: String?) {

//        init()

        log.info("************************************************")
        log.info("Endpoint is: ${environments.endpoint}")
        log.info("Backup dir is: ${harborProps.backupDir}")
        log.info("************************************************")
    }
}

fun main(args: Array<String>) {
    runApplication<HarborApplication>(*args)
}
