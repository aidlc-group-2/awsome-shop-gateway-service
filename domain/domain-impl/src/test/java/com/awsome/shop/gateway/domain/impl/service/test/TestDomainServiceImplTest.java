package com.awsome.shop.gateway.domain.impl.service.test;

import com.awsome.shop.gateway.common.dto.PageResult;
import com.awsome.shop.gateway.common.exception.BusinessException;
import com.awsome.shop.gateway.domain.model.test.TestEntity;
import com.awsome.shop.gateway.repository.test.TestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TestDomainServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TestDomainServiceImplTest {

    @Mock
    private TestRepository testRepository;

    @InjectMocks
    private TestDomainServiceImpl testDomainService;

    @Test
    @DisplayName("getById 存在时返回实体")
    void getByIdShouldReturnEntity() {
        TestEntity entity = new TestEntity();
        entity.setId(1L);
        when(testRepository.getById(1L)).thenReturn(entity);

        assertThat(testDomainService.getById(1L)).isSameAs(entity);
    }

    @Test
    @DisplayName("getById 不存在时抛 BusinessException")
    void getByIdShouldThrowWhenNotFound() {
        when(testRepository.getById(99L)).thenReturn(null);

        assertThatThrownBy(() -> testDomainService.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("page 应透传分页参数")
    void pageShouldDelegate() {
        PageResult<TestEntity> page = new PageResult<>();
        when(testRepository.page(1, 20, "abc")).thenReturn(page);

        assertThat(testDomainService.page(1, 20, "abc")).isSameAs(page);
    }

    @Test
    @DisplayName("create 应保存并回查返回")
    void createShouldSaveAndReload() {
        doAnswer(invocation -> {
            TestEntity e = invocation.getArgument(0);
            e.setId(10L);
            return null;
        }).when(testRepository).save(any(TestEntity.class));
        TestEntity reloaded = new TestEntity();
        reloaded.setId(10L);
        when(testRepository.getById(10L)).thenReturn(reloaded);

        TestEntity result = testDomainService.create("名称", "描述");

        assertThat(result).isSameAs(reloaded);
        ArgumentCaptor<TestEntity> captor = ArgumentCaptor.forClass(TestEntity.class);
        verify(testRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("名称");
        assertThat(captor.getValue().getDescription()).isEqualTo("描述");
    }

    @Test
    @DisplayName("update 应修改字段并回查返回")
    void updateShouldModifyAndReload() {
        TestEntity existing = new TestEntity();
        existing.setId(1L);
        existing.setName("旧");
        TestEntity reloaded = new TestEntity();
        reloaded.setId(1L);
        when(testRepository.getById(1L)).thenReturn(existing, reloaded);

        TestEntity result = testDomainService.update(1L, "新", "新描述");

        assertThat(existing.getName()).isEqualTo("新");
        assertThat(existing.getDescription()).isEqualTo("新描述");
        verify(testRepository).update(existing);
        assertThat(result).isSameAs(reloaded);
    }

    @Test
    @DisplayName("update 不存在时抛异常且不更新")
    void updateShouldThrowWhenNotFound() {
        when(testRepository.getById(99L)).thenReturn(null);

        assertThatThrownBy(() -> testDomainService.update(99L, "n", "d"))
                .isInstanceOf(BusinessException.class);
        verify(testRepository, never()).update(any());
    }

    @Test
    @DisplayName("delete 存在时执行删除")
    void deleteShouldRemoveExistingEntity() {
        when(testRepository.getById(1L)).thenReturn(new TestEntity());

        testDomainService.delete(1L);

        verify(testRepository).deleteById(1L);
    }

    @Test
    @DisplayName("delete 不存在时抛异常且不删除")
    void deleteShouldThrowWhenNotFound() {
        when(testRepository.getById(99L)).thenReturn(null);

        assertThatThrownBy(() -> testDomainService.delete(99L))
                .isInstanceOf(BusinessException.class);
        verify(testRepository, never()).deleteById(any());
    }
}
