package com.fasterxml.jackson.databind.ser;

import java.lang.reflect.Method;
import java.util.Map;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.ser.std.MapSerializer;


/**
 * Class similar to {@link BeanPropertyWriter}, but that will be used
 * for serializing {@link org.codehaus.jackson.annotate.JsonAnyGetter} annotated
 * (Map) properties
 * 
 * @since 1.6
 */
public class AnyGetterWriter
{
    protected final Method _anyGetter;
    
    protected final MapSerializer _serializer;
    
    public AnyGetterWriter(AnnotatedMethod anyGetter, MapSerializer serializer)
    {
        _anyGetter = anyGetter.getAnnotated();
        _serializer = serializer;
    }

    public void getAndSerialize(Object bean, JsonGenerator jgen, SerializerProvider provider)
        throws Exception
    {
        Object value = _anyGetter.invoke(bean);
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?,?>)) {
            throw new JsonMappingException("Value returned by 'any-getter' ("+_anyGetter.getName()+"()) not java.util.Map but "
                    +value.getClass().getName());
        }
        _serializer.serializeFields((Map<?,?>) value, jgen, provider);
    }

    public void resolve(SerializerProvider provider) throws JsonMappingException
    {
        _serializer.resolve(provider);
    }
}
