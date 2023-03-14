package julyww.harbor

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties
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
@SpringBootApplication
class HarborApplication {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = [
        IllegalStateException::class,
        IllegalArgumentException::class
    ])
    fun exceptionHandler(e: Exception): String? {
        return e.message
    }
}

fun main(args: Array<String>) {
    runApplication<HarborApplication>(*args)
}
