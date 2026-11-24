package net.hwyz.iov.cloud.framework.web.handler;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import net.hwyz.iov.cloud.framework.common.bean.ApiResponse;
import net.hwyz.iov.cloud.framework.common.exception.BusinessException;
import net.hwyz.iov.cloud.framework.common.exception.ErrorCode;
import net.hwyz.iov.cloud.framework.common.exception.HttpStatusAware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统一异常出口契约测试（FW-WEB-DSN-CR-004）。
 * <p>
 * 断言两种错误响应面：管理面（声明返回 ApiResponse）业务错误 HTTP 200 + 业务码；
 * 机器面（声明返回裸 DTO/void）业务错误非 2xx + 同一 ApiResponse 错误体。
 * </p>
 *
 * @author hwyz_leo
 */
@DisplayName("FW-WEB-DSN-CR-004 统一异常出口契约")
class ErrorResponseContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EnvelopeController(), new MachineController())
                .setControllerAdvice(new ValidationExceptionHandler(),
                        new WebRequestExceptionHandler(),
                        new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("管理面：业务异常返回 HTTP 200 + 业务码 + 可展示文案")
    void envelopeBusinessError() throws Exception {
        mockMvc.perform(get("/envelope/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("999001"))
                .andExpect(jsonPath("$.message").value("业务拒绝"));
    }

    @Test
    @DisplayName("管理面：ResponseEntity<ApiResponse> 端点同样按信封语义返回 200")
    void envelopeResponseEntityBusinessError() throws Exception {
        mockMvc.perform(get("/envelope/entity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("999001"));
    }

    @Test
    @DisplayName("管理面：错误详情作为 message 外显，便于运营定位")
    void envelopeBusinessErrorDetail() throws Exception {
        mockMvc.perform(get("/envelope/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("999001"))
                .andExpect(jsonPath("$.message").value("资产映射不存在"));
    }

    @Test
    @DisplayName("管理面：缺参仍按信封语义返回 200 + 100001")
    void envelopeClientError() throws Exception {
        mockMvc.perform(get("/envelope/param"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("100001"));
    }

    @Test
    @DisplayName("管理面：未预期异常返回 HTTP 500 + 100002，不暴露异常细节")
    void envelopeSystemError() throws Exception {
        mockMvc.perform(get("/envelope/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("100002"))
                .andExpect(jsonPath("$.message").value("系统内部错误"));
    }

    @Test
    @DisplayName("机器面：业务异常返回 HTTP 422 + 业务码（不得 200）")
    void machineBusinessError() throws Exception {
        mockMvc.perform(get("/machine/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("999001"))
                .andExpect(jsonPath("$.message").value("业务拒绝"));
    }

    @Test
    @DisplayName("机器面：错误码声明状态优先（409）")
    void machineDeclaredStatus() throws Exception {
        mockMvc.perform(get("/machine/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("999002"));
    }

    @Test
    @DisplayName("机器面：void 端点业务异常同样非 2xx")
    void machineVoidBusinessError() throws Exception {
        mockMvc.perform(get("/machine/void"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("999001"));
    }

    @Test
    @DisplayName("机器面：Bean Validation 失败返回 HTTP 400 + 100001")
    void machineBeanValidationError() throws Exception {
        mockMvc.perform(post("/machine/valid").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("100001"));
    }

    @Test
    @DisplayName("管理面：Bean Validation 失败保持 HTTP 200 + 100001")
    void envelopeBeanValidationError() throws Exception {
        mockMvc.perform(post("/envelope/valid").contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("100001"));
    }

    @Test
    @DisplayName("机器面：缺参返回 HTTP 400 + 100001")
    void machineMissingParameter() throws Exception {
        mockMvc.perform(get("/machine/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("100001"));
    }

    @Test
    @DisplayName("机器面：未预期异常返回 HTTP 500 + 100002")
    void machineSystemError() throws Exception {
        mockMvc.perform(get("/machine/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("100002"));
    }

    /**
     * 测试错误码：默认 422，冲突码声明 409。
     */
    enum TestErrorCode implements ErrorCode, HttpStatusAware {
        BIZ("999001", "业务拒绝"),
        CONFLICT("999002", "状态冲突") {
            @Override
            public int getHttpStatus() {
                return 409;
            }
        };

        private final String code;
        private final String message;

        TestErrorCode(String code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    /**
     * 测试请求体。
     */
    @Data
    public static class Payload {
        @NotBlank(message = "name不能为空")
        private String name;
    }

    /**
     * 测试响应体（机器面裸契约）。
     */
    @Data
    public static class MachineVo {
        private String requestId;
    }

    /**
     * 管理面端点：声明返回 ApiResponse。
     */
    @RestController
    @RequestMapping("/envelope")
    static class EnvelopeController {

        @GetMapping("/business")
        ApiResponse<Void> business() {
            throw new BusinessException(TestErrorCode.BIZ);
        }

        @GetMapping("/detail")
        ApiResponse<Void> detail() {
            throw new BusinessException(TestErrorCode.BIZ, "资产映射不存在");
        }

        @GetMapping("/entity")
        ResponseEntity<ApiResponse<Void>> entity() {
            throw new BusinessException(TestErrorCode.BIZ);
        }

        @GetMapping("/boom")
        ApiResponse<Void> boom() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/param")
        ApiResponse<Void> param(@RequestParam("id") String id) {
            return ApiResponse.ok();
        }

        @PostMapping("/valid")
        ApiResponse<Void> valid(@RequestBody @Valid Payload payload) {
            return ApiResponse.ok();
        }
    }

    /**
     * 机器面端点：声明返回裸 DTO/void。
     */
    @RestController
    @RequestMapping("/machine")
    static class MachineController {

        @GetMapping("/business")
        MachineVo business() {
            throw new BusinessException(TestErrorCode.BIZ);
        }

        @GetMapping("/conflict")
        MachineVo conflict() {
            throw new BusinessException(TestErrorCode.CONFLICT);
        }

        @GetMapping("/void")
        void voidEndpoint() {
            throw new BusinessException(TestErrorCode.BIZ);
        }

        @GetMapping("/boom")
        MachineVo boom() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/param")
        MachineVo param(@RequestParam("id") String id) {
            return new MachineVo();
        }

        @PostMapping("/valid")
        MachineVo valid(@RequestBody @Valid Payload payload) {
            return new MachineVo();
        }
    }
}
