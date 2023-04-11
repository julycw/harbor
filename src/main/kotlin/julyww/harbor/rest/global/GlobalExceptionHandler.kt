package julyww.harbor.rest.global

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

class TargetNotRegisteredException(msg: String): Exception(msg)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(
        value = [
            IllegalStateException::class,
            IllegalArgumentException::class
        ]
    )
    fun handleError(e: Exception): String? {
        e.printStackTrace()
        return e.message
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(
        value = [
            NullPointerException::class
        ]
    )
    fun handleNullError(e: NullPointerException): String? {
        e.printStackTrace()
        return e.message
    }

    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(
        value = [
            TargetNotRegisteredException::class
        ]
    )
    fun handleBadGateway(e: TargetNotRegisteredException): String? {
        return e.message
    }
}