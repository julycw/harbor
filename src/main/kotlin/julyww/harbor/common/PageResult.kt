package julyww.harbor.common

data class PageResult<T>(
    val total: Long,
    val list: List<T>
)