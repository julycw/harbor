package julyww.harbor.remote

import cn.trustway.nb.common.auth.common.RoleType
import cn.trustway.nb.common.auth.service.role.Permission
import cn.trustway.nb.common.auth.service.role.PermissionOptions
import julyww.harbor.conf.AuthFeignClientConfig
import org.springframework.boot.CommandLineRunner
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

const val AUTH_SERVICE = "AUTH-SERVICE"

@FeignClient(name = AUTH_SERVICE, contextId = "AuthPermissionService", configuration = [AuthFeignClientConfig::class])
interface PermissionService {

    @PutMapping("/auth/inner/permission/additional")
    fun addPermission(@RequestBody permissions: List<Permission>)
}

const val SystemModuleList = "system:app-module:list"
const val SystemModuleManage = "system:app-module:manage"
const val SystemHostList = "system:host:list"
const val SystemHostManage = "system:host:manage"
const val SystemSection = "系统管理"

@Component
class PermissionInitializer(
    private val permissionService: PermissionService
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        permissionService.addPermission(
            listOf(
                Permission.of(
                    SystemModuleList, "应用功能模块查看",
                    SystemSection, "系统管理", RoleType.System, PermissionOptions.defaults()
                ),
                Permission.of(SystemModuleManage, "应用功能模块控制",
                    SystemSection, "系统管理", RoleType.System, PermissionOptions.defaults().apply {
                        isRegular = false
                    }
                ),
                Permission.of(
                    SystemHostList, "服务器列表查看",
                    SystemSection, "系统管理", RoleType.System, PermissionOptions.defaults()
                ),
                Permission.of(
                    SystemHostManage, "服务器信息管理",
                    SystemSection, "系统管理", RoleType.System, PermissionOptions.defaults().apply {
                        isRegular = false
                    }
                )
            )
        )
    }
}