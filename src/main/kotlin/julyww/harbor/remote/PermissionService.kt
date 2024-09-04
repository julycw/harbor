package julyww.harbor.remote

import cn.trustway.nb.common.auth.autoconfig.init.AppInitService
import cn.trustway.nb.common.auth.autoconfig.init.permission.PermissionRegister
import cn.trustway.nb.common.auth.common.RoleType
import cn.trustway.nb.common.auth.service.role.Permission
import cn.trustway.nb.common.auth.service.role.PermissionOptions
import org.springframework.stereotype.Component

const val SystemModuleList = "system:app-module:list"
const val SystemCertificationManage = "system:certification:manage"
const val SystemModuleManage = "system:app-module:manage"
const val SystemHostList = "system:host:list"
const val SystemHostManage = "system:host:manage"
const val SystemSection = "系统管理"

@Component
class PermissionInitializer : AppInitService {

    override fun init(register: PermissionRegister) {
        register.register(
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
                ),
                Permission.of(
                    SystemCertificationManage, "系统凭证管理",
                    SystemSection, "系统管理", RoleType.System, PermissionOptions.defaults()
                ),
            )
        )
    }
}