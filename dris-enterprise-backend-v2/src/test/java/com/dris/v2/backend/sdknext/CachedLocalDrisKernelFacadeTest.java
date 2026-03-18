package com.dris.v2.backend.sdknext;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.domain.Resource;
import com.dris.sdk.next.enumtype.ObjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class CachedLocalDrisKernelFacadeTest {
    @Mock
    private LocalDrisKernelFacade delegate;
    private CachedLocalDrisKernelFacade facade;
    @BeforeEach
    void setUp() {
        BackendV2Properties properties = new BackendV2Properties();
        BackendV2Properties.KernelReadCacheProperties cache = new BackendV2Properties.KernelReadCacheProperties();
        cache.setEnabled(true);
        cache.setTtlMs(60000L);
        cache.setMaxEntries(32);
        properties.setKernelReadCache(cache);
        facade = new CachedLocalDrisKernelFacade(delegate, properties);
    }
    @Test
    void existsIdentifier_shouldUseCache() {
        when(delegate.existsIdentifier("1100ABCD019320260101AAAAAAAAAA")).thenReturn(Boolean.TRUE);
        assertTrue(facade.existsIdentifier("1100ABCD019320260101AAAAAAAAAA"));
        assertTrue(facade.existsIdentifier("1100ABCD019320260101AAAAAAAAAA"));
        verify(delegate, times(1)).existsIdentifier("1100ABCD019320260101AAAAAAAAAA");
    }
    @Test
    void getMetadata_shouldReturnCopyFromCache() {
        IdentifierMetadata metadata = metadata("n1");
        when(delegate.getMetadata("1100ABCD019320260101AAAAAAAAAA")).thenReturn(metadata);
        IdentifierMetadata first = facade.getMetadata("1100ABCD019320260101AAAAAAAAAA");
        first.setName("mutated");
        IdentifierMetadata second = facade.getMetadata("1100ABCD019320260101AAAAAAAAAA");
        assertEquals("n1", second.getName());
        verify(delegate, times(1)).getMetadata("1100ABCD019320260101AAAAAAAAAA");
    }
    @Test
    void updateMetadata_shouldInvalidateCache() {
        IdentifierMetadata before = metadata("before");
        IdentifierMetadata after = metadata("after");
        when(delegate.getMetadata("1100ABCD019320260101AAAAAAAAAA")).thenReturn(before, after);
        when(delegate.updateMetadata("1100ABCD019320260101AAAAAAAAAA", after)).thenReturn(Boolean.TRUE);
        assertEquals("before", facade.getMetadata("1100ABCD019320260101AAAAAAAAAA").getName());
        facade.updateMetadata("1100ABCD019320260101AAAAAAAAAA", after);
        assertEquals("after", facade.getMetadata("1100ABCD019320260101AAAAAAAAAA").getName());
        verify(delegate, times(2)).getMetadata("1100ABCD019320260101AAAAAAAAAA");
    }
    @Test
    void getDigitalObject_shouldWarmMetadataAndResourceCaches() {
        DigitalObject object = new DigitalObject();
        object.setIdentifier("1100ABCD019320260101AAAAAAAAAA");
        object.setStatus(ObjectStatus.ACTIVE);
        object.setMetadata(metadata("from-object"));
        object.setResource(resource("https://example.com/a.json"));
        when(delegate.getDigitalObject("1100ABCD019320260101AAAAAAAAAA")).thenReturn(object);
        DigitalObject loaded = facade.getDigitalObject("1100ABCD019320260101AAAAAAAAAA");
        assertEquals("from-object", loaded.getMetadata().getName());
        assertEquals("from-object", facade.getMetadata("1100ABCD019320260101AAAAAAAAAA").getName());
        assertEquals("https://example.com/a.json", facade.getResource("1100ABCD019320260101AAAAAAAAAA").getLocator());
        verify(delegate, times(1)).getDigitalObject("1100ABCD019320260101AAAAAAAAAA");
        verify(delegate, times(0)).getMetadata("1100ABCD019320260101AAAAAAAAAA");
        verify(delegate, times(0)).getResource("1100ABCD019320260101AAAAAAAAAA");
    }
    @Test
    void upsertResource_shouldInvalidateResourceCache() {
        Resource before = resource("https://example.com/old.json");
        Resource after = resource("https://example.com/new.json");
        when(delegate.getResource("1100ABCD019320260101AAAAAAAAAA")).thenReturn(before, after);
        when(delegate.upsertResource("1100ABCD019320260101AAAAAAAAAA", after, false)).thenReturn(Boolean.TRUE);
        assertEquals("https://example.com/old.json", facade.getResource("1100ABCD019320260101AAAAAAAAAA").getLocator());
        facade.upsertResource("1100ABCD019320260101AAAAAAAAAA", after, false);
        assertEquals("https://example.com/new.json", facade.getResource("1100ABCD019320260101AAAAAAAAAA").getLocator());
        verify(delegate, times(2)).getResource("1100ABCD019320260101AAAAAAAAAA");
    }
    private IdentifierMetadata metadata(String name) {
        IdentifierMetadata metadata = new IdentifierMetadata();
        metadata.setIdentifier("1100ABCD019320260101AAAAAAAAAA");
        metadata.setName(name);
        metadata.setAdditionalAttributes(Collections.<String, Object>emptyMap());
        return metadata;
    }
    private Resource resource(String locator) {
        Resource resource = new Resource();
        resource.setComponentType("resource");
        resource.setStorageType("external_uri");
        resource.setAccessMethod("download");
        resource.setContentType("application/json");
        resource.setFormat("json");
        resource.setLocator(locator);
        return resource;
    }
}