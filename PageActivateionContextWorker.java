// Copyright 2008, 2009, 2010, 2011 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.tapestry5.internal.transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.tapestry5.EventConstants;
import org.apache.tapestry5.EventContext;
import org.apache.tapestry5.annotations.PageActivationContext;
import org.apache.tapestry5.ioc.internal.util.CollectionFactory;
import org.apache.tapestry5.ioc.internal.util.InternalUtils;
import org.apache.tapestry5.model.MutableComponentModel;
import org.apache.tapestry5.plastic.FieldHandle;
import org.apache.tapestry5.plastic.PlasticClass;
import org.apache.tapestry5.plastic.PlasticField;
import org.apache.tapestry5.runtime.Component;
import org.apache.tapestry5.runtime.ComponentEvent;
import org.apache.tapestry5.services.ComponentEventHandler;
import org.apache.tapestry5.services.transform.ComponentClassTransformWorker2;
import org.apache.tapestry5.services.transform.TransformationSupport;

/**
 * Provides the page activation context handlers.
 *
 * @see org.apache.tapestry5.annotations.PageActivationContext
 */
public class PageActivationContextWorker implements ComponentClassTransformWorker2
{
    public void transform(PlasticClass plasticClass, TransformationSupport support, MutableComponentModel model)
    {
        List<PlasticField> fields = plasticClass.getFieldsWithAnnotation(PageActivationContext.class);

        if (!fields.isEmpty())
        {
            transformFields(support, fields);
        }
    }

    private void transformFields(TransformationSupport support, List<PlasticField> fields)
    {
        List<PlasticField> sortedFields = sortByIndex(fields);
        validateSortedFields(sortedFields);
        
        PlasticField firstField = sortedFields.get(0);
        PageActivationContext firstAnnotation = firstField.getAnnotation(PageActivationContext.class);

        FieldHandle[] handles = new FieldHandle[sortedFields.size()];
        String[] typeNames = new String[sortedFields.size()];
        int i = 0;
        for (PlasticField field : sortedFields) {
            handles[i] = field.getHandle();
            typeNames[i] = field.getTypeName();
            ++i;
        }
        
        if (firstAnnotation.activate())
        {
            support.addEventHandler(EventConstants.ACTIVATE, 1,
                    "PageActivationContextWorker activate event handler",
                    createActivationHandler(typeNames, handles));
        }

        if (firstAnnotation.passivate())
        {
            support.addEventHandler(EventConstants.PASSIVATE, 0,
                    "PageActivationContextWorker passivate event handler", createPassivateHandler(handles));
        }

        // We don't claim the field, and other workers may even replace it with a FieldConduit.
    }

    private void validateSortedFields(List<PlasticField> sortedFields) {
        if (sortedFields.size() < 2)
        {
            return;
        }
        List<Integer> expectedIndexes = CollectionFactory.newList();
        List<Integer> actualIndexes = CollectionFactory.newList();
        Set<Boolean> activateFlags = CollectionFactory.newSet();
        Set<Boolean> passivateFlags = CollectionFactory.newSet();
        
        for (int i = 0; i < sortedFields.size(); ++i) {
            PlasticField field = sortedFields.get(i);
            PageActivationContext ann = field.getAnnotation(PageActivationContext.class);
            expectedIndexes.add(i);
            actualIndexes.add(ann.index());
            activateFlags.add(ann.activate());
            passivateFlags.add(ann.passivate());
        }
        
        List<String> errors = CollectionFactory.newList(); 
        if (!expectedIndexes.equals(actualIndexes)) {
            errors.add(String.format("Illegal indexes - expected %s, found %s", expectedIndexes, actualIndexes));
        }
        if (activateFlags.size() > 1) {
            errors.add("Illegal activate flags, all @PageActivationContext fields must have the same activate flag");
        }
        if (passivateFlags.size() > 1) {
            errors.add("Illegal passivate flags, all @PageActivationContext fields must have the same passivate flag");
        }
        if (!errors.isEmpty()) {
            throw new RuntimeException(String.format("Invalid values for @PageActivationContext: %s", InternalUtils.join(errors)));
        }
    }
    
    private List<PlasticField> sortByIndex(List<PlasticField> fields)
    {
        List<PlasticField> sortedFields = new ArrayList<PlasticField>(fields);
        Collections.sort(sortedFields, new Comparator<PlasticField>() {
            public int compare(PlasticField field1, PlasticField field2) {
                int index1 = field1.getAnnotation(PageActivationContext.class).index();
                int index2 = field2.getAnnotation(PageActivationContext.class).index();

                int compare = new Integer(index1).compareTo(index2);
                if (compare == 0)
                {
                    compare = field1.getName().compareTo(field2.getName());
                }
                return compare;
            }
        });
        return sortedFields;
    }

    private static ComponentEventHandler createActivationHandler(final String[] fieldTypes, final FieldHandle[] handles)
    {
        return new ComponentEventHandler()
        {
            public void handleEvent(Component instance, ComponentEvent event)
            {
                EventContext eventContext = event.getEventContext();
                for (int i = 0; i < eventContext.getCount(); ++i)
                {
                    String fieldType = fieldTypes[i];
                    FieldHandle handle = handles[i];
                    Object value = event.coerceContext(i, fieldType);
                    handle.set(instance, value);
                }
            }
        };
    }

    private static ComponentEventHandler createPassivateHandler(final FieldHandle[] handles)
    {
        return new ComponentEventHandler()
        {
            public void handleEvent(Component instance, ComponentEvent event)
            {
                LinkedList<Object> list = CollectionFactory.newLinkedList();
                for (int i = handles.length - 1; i >= 0; i--) {
                    FieldHandle handle = handles[i];
                    Object value = handle.get(instance);

                    // ignore trailing nulls
                    if (value != null || !list.isEmpty()) {
                        list.addFirst(value);
                    }
                }

                event.storeResult(list);
            }
        };
    }
}
