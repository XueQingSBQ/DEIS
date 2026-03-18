package com.dris.v2.backend.service;

import com.dris.v2.backend.TestSamples;
import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.sdknext.LocalDrisKernelFacade;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.contract.DrisResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolExecutionLocalServiceTest {
    @Mock
    private LocalDrisKernelFacade localDrisKernelFacade;
    @InjectMocks
    private ToolExecutionLocalService service;

    @Test
    void executeTool_missingCommand_shouldFail() {
        DigitalObject object = new DigitalObject();
        object.setIdentifier(TestSamples.VALID_IDENTIFIER);
        Resource tool = new Resource();
        tool.setComponentType("tool");
        tool.setStorageType("local_exec");
        tool.setAccessMethod("invoke");
        tool.setContentType("tool");
        tool.setFormat("json");
        tool.setLocator("local://tools/noop");
        object.setResource(tool);

        when(localDrisKernelFacade.getDigitalObject(TestSamples.VALID_IDENTIFIER)).thenReturn(object);
        DrisResponse<ToolExecuteResult> out = service.executeToolLocal(TestSamples.VALID_IDENTIFIER, new ToolExecuteRequest());
        assertTrue(!out.isOk());
    }

    @Test
    void executeTool_valid_shouldReturnResult() {
        DigitalObject object = new DigitalObject();
        object.setIdentifier(TestSamples.VALID_IDENTIFIER);
        Resource tool = new Resource();
        tool.setComponentType("tool");
        tool.setStorageType("local_exec");
        tool.setAccessMethod("invoke");
        tool.setContentType("tool");
        tool.setFormat("json");
        tool.setLocator("local://tools/sum");

        Map<String, Object> extra = new LinkedHashMap<String, Object>();
        extra.put("operationName", "sum");
        extra.put("commandWindows", Arrays.asList("cmd", "/c", "set /a ${a}+${b}"));
        extra.put("commandUnix", Arrays.asList("sh", "-lc", "expr ${a} + ${b}"));
        tool.setExtra(extra);
        object.setResource(tool);

        when(localDrisKernelFacade.getDigitalObject(TestSamples.VALID_IDENTIFIER)).thenReturn(object);

        ToolExecuteRequest request = new ToolExecuteRequest();
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("a", 7);
        args.put("b", 5);
        request.setArgs(args);
        request.setTimeoutMs(5000L);

        DrisResponse<ToolExecuteResult> out = service.executeToolLocal(TestSamples.VALID_IDENTIFIER, request);
        assertTrue(out.isOk());
        assertTrue(out.getData().isSuccess());
        assertTrue(out.getData().getStdout() != null && out.getData().getStdout().contains("12"));
    }
}

