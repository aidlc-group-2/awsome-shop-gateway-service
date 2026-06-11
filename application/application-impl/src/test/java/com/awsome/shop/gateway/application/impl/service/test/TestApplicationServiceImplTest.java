package com.awsome.shop.gateway.application.impl.service.test;

import com.awsome.shop.gateway.application.api.dto.test.TestDTO;
import com.awsome.shop.gateway.application.api.dto.test.request.CreateTestRequest;
import com.awsome.shop.gateway.application.api.dto.test.request.DeleteTestRequest;
import com.awsome.shop.gateway.application.api.dto.test.request.GetTestRequest;
import com.awsome.shop.gateway.application.api.dto.test.request.ListTestRequest;
import com.awsome.shop.gateway.application.api.dto.test.request.UpdateTestRequest;
import com.awsome.shop.gateway.common.dto.PageResult;
import com.awsome.shop.gateway.domain.model.test.TestEntity;
import com.awsome.shop.gateway.domain.service.test.TestDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TestApplicationServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TestApplicationServiceImplTest {

    @Mock
    private TestDomainService testDomainService;

    @InjectMocks
    private TestApplicationServiceImpl testApplicationService;

    private TestEntity entity() {
        TestEntity entity = new TestEntity();
        entity.setId(1L);
        entity.setName("名称");
        entity.setDescription("描述");
        entity.setCreatedAt(LocalDateTime.of(2026, 6, 1, 0, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 6, 10, 0, 0));
        return entity;
    }

    private void assertDtoMatchesEntity(TestDTO dto) {
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("名称");
        assertThat(dto.getDescription()).isEqualTo("描述");
        assertThat(dto.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(dto.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 10, 0, 0));
    }

    @Test
    @DisplayName("get 应返回映射后的 DTO")
    void getShouldMapEntityToDto() {
        when(testDomainService.getById(1L)).thenReturn(entity());

        GetTestRequest request = new GetTestRequest();
        request.setId(1L);

        assertDtoMatchesEntity(testApplicationService.get(request));
    }

    @Test
    @DisplayName("list 应转换分页结果")
    void listShouldConvertPageResult() {
        PageResult<TestEntity> page = new PageResult<>();
        page.setCurrent(1L);
        page.setSize(20L);
        page.setTotal(1L);
        page.setPages(1L);
        page.setRecords(List.of(entity()));
        when(testDomainService.page(1, 20, "名称")).thenReturn(page);

        ListTestRequest request = new ListTestRequest();
        request.setPage(1);
        request.setSize(20);
        request.setName("名称");

        PageResult<TestDTO> result = testApplicationService.list(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        assertDtoMatchesEntity(result.getRecords().get(0));
    }

    @Test
    @DisplayName("create 应委托领域服务并映射 DTO")
    void createShouldDelegateAndMap() {
        when(testDomainService.create("名称", "描述")).thenReturn(entity());

        CreateTestRequest request = new CreateTestRequest();
        request.setName("名称");
        request.setDescription("描述");

        assertDtoMatchesEntity(testApplicationService.create(request));
        verify(testDomainService).create("名称", "描述");
    }

    @Test
    @DisplayName("update 应委托领域服务并映射 DTO")
    void updateShouldDelegateAndMap() {
        when(testDomainService.update(1L, "名称", "描述")).thenReturn(entity());

        UpdateTestRequest request = new UpdateTestRequest();
        request.setId(1L);
        request.setName("名称");
        request.setDescription("描述");

        assertDtoMatchesEntity(testApplicationService.update(request));
    }

    @Test
    @DisplayName("delete 应委托领域服务")
    void deleteShouldDelegate() {
        DeleteTestRequest request = new DeleteTestRequest();
        request.setId(1L);

        testApplicationService.delete(request);

        verify(testDomainService).delete(1L);
    }
}
