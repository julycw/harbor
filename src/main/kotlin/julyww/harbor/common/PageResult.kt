package julyww.harbor.common

data class PageResult<T>(
    val total: Int,
    val list: List<T>
)