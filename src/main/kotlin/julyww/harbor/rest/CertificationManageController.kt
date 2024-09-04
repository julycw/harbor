package julyww.harbor.rest

import cn.trustway.nb.common.auth.annotation.auth.RequiresAuthentication
import cn.trustway.nb.common.auth.annotation.auth.RequiresPermissions
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import julyww.harbor.core.certification.CertificationDTO
import julyww.harbor.core.certification.CertificationService
import julyww.harbor.remote.SystemCertificationManage
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Api(tags = ["凭证管理"])
@RequiresPermissions(SystemCertificationManage)
@RequiresAuthentication
@RequestMapping("certification")
@RestController
class CertificationManageController(
    private val certificationService: CertificationService
) {

    @ApiOperation("获取凭证列表")
    @GetMapping
    fun list(): List<CertificationDTO> {
        return certificationService.listAll()
    }

    @ApiOperation("保存凭证")
    @PostMapping
    fun save(@RequestBody certification: CertificationDTO) {
        certificationService.save(certification)
    }

    @ApiOperation("删除凭证")
    @DeleteMapping("{id}")
    fun delete(@PathVariable id: String) {
        certificationService.delete(id)
    }


}