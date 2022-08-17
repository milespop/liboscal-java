/*
 * Portions of this software was developed by employees of the National Institute
 * of Standards and Technology (NIST), an agency of the Federal Government and is
 * being made available as a public service. Pursuant to title 17 United States
 * Code Section 105, works of NIST employees are not subject to copyright
 * protection in the United States. This software may be subject to foreign
 * copyright. Permission in the United States and in foreign countries, to the
 * extent that NIST may hold copyright, to use, copy, modify, create derivative
 * works, and distribute this software and its documentation without fee is hereby
 * granted on a non-exclusive basis, provided that this notice and disclaimer
 * of warranty appears in all copies.
 *
 * THE SOFTWARE IS PROVIDED 'AS IS' WITHOUT ANY WARRANTY OF ANY KIND, EITHER
 * EXPRESSED, IMPLIED, OR STATUTORY, INCLUDING, BUT NOT LIMITED TO, ANY WARRANTY
 * THAT THE SOFTWARE WILL CONFORM TO SPECIFICATIONS, ANY IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND FREEDOM FROM
 * INFRINGEMENT, AND ANY WARRANTY THAT THE DOCUMENTATION WILL CONFORM TO THE
 * SOFTWARE, OR ANY WARRANTY THAT THE SOFTWARE WILL BE ERROR FREE.  IN NO EVENT
 * SHALL NIST BE LIABLE FOR ANY DAMAGES, INCLUDING, BUT NOT LIMITED TO, DIRECT,
 * INDIRECT, SPECIAL OR CONSEQUENTIAL DAMAGES, ARISING OUT OF, RESULTING FROM,
 * OR IN ANY WAY CONNECTED WITH THIS SOFTWARE, WHETHER OR NOT BASED UPON WARRANTY,
 * CONTRACT, TORT, OR OTHERWISE, WHETHER OR NOT INJURY WAS SUSTAINED BY PERSONS OR
 * PROPERTY OR OTHERWISE, AND WHETHER OR NOT LOSS WAS SUSTAINED FROM, OR AROSE OUT
 * OF THE RESULTS OF, OR USE OF, THE SOFTWARE OR SERVICES PROVIDED HEREUNDER.
 */

package gov.nist.secauto.oscal.lib.profile.resolver;

import gov.nist.secauto.metaschema.model.common.datatype.adapter.UuidAdapter;
import gov.nist.secauto.metaschema.model.common.metapath.MetapathExpression;
import gov.nist.secauto.metaschema.model.common.metapath.MetapathExpression.ResultType;
import gov.nist.secauto.metaschema.model.common.util.CollectionUtil;
import gov.nist.secauto.metaschema.model.common.util.CustomCollectors;
import gov.nist.secauto.metaschema.model.common.util.ObjectUtils;
import gov.nist.secauto.oscal.lib.model.control.catalog.ICatalog;
import gov.nist.secauto.oscal.lib.model.control.catalog.IControlContainer;
import gov.nist.secauto.oscal.lib.profile.resolver.EntityItem.ItemType;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class Index {
  public static final MetapathExpression HAS_PROP_KEEP = MetapathExpression
      .compile("prop[@name='keep' and has-oscal-namespace('http://csrc.nist.gov/ns/oscal')]/@value = 'always'");

  @NonNull
  private final Map<ItemType, ItemGroup> entityMap;
  @NonNull
  private final Set<IControlContainer> selectedContainers;

  public static <T, K> Stream<T> merge(
      @NonNull Stream<T> resolvedItems,
      @NonNull Index index,
      @NonNull ItemType itemType,
      @NonNull Function<? super T, ? extends K> keyMapper) {
    @SuppressWarnings("unchecked")
    Stream<T> importedStream = index.getEntitiesByItemType(itemType).stream()
        .filter(entity -> {
          return entity.getReferenceCount() > 0
              || (Boolean) HAS_PROP_KEEP.evaluateAs(entity.getInstance(), ResultType.BOOLEAN);
        })
        .map(entity -> (T) entity.getInstanceValue());

    return CustomCollectors.distinctByKey(
        Stream.concat(resolvedItems, importedStream),
        keyMapper,
        (key, value1, value2) -> value2);
  }

  @SuppressWarnings("null")
  public Index() {
    this.entityMap = new EnumMap<>(ItemType.class);
    this.selectedContainers = new HashSet<>();
  }

  public void markSelected(@NonNull IControlContainer container) {
    selectedContainers.add(container);
  }

  public boolean isSelected(@NonNull IControlContainer container) {
    return container instanceof ICatalog || selectedContainers.contains(container);
  }

  @Nullable
  protected ItemGroup getItemGroup(@NonNull ItemType itemType) {
    return entityMap.get(itemType);
  }

  protected ItemGroup newItemGroup(@NonNull ItemType itemType) {
    ItemGroup retval = new ItemGroup(itemType);
    entityMap.put(itemType, retval);
    return retval;
  }

  @NonNull
  public Collection<EntityItem>
      getEntitiesByItemType(@NonNull ItemType itemType) {
    ItemGroup group = getItemGroup(itemType);
    return group == null ? CollectionUtil.emptyList() : group.getEntities();
  }

  public EntityItem getEntity(@NonNull ItemType itemType, @NonNull UUID identifier) {
    return getEntity(itemType, identifier.toString());
  }

  public EntityItem getEntity(@NonNull ItemType itemType, @NonNull String identifier) {
    ItemGroup group = getItemGroup(itemType);

    String normalizedIdentifier = EntityItem.normalizeIdentifier(itemType, identifier);
    return group == null ? null : group.getEntity(normalizedIdentifier);
  }

  public <T> EntityItem addItem(@NonNull EntityItem item) {
    ItemType type = item.getItemType();

    ItemGroup group = getItemGroup(type);
    if (group == null) {
      group = newItemGroup(type);
    }
    return group.add(item);
  }

  //
  // public int getParameterReferenceCount(String parameterId) {
  // Integer count = parameterReferenceCountMap.get(parameterId);
  // if (count == null) {
  // count = 0;
  // }
  // return count;
  // }
  //
  // public void incrementParameterReferenceCount(@NonNull String parameterId) {
  // int count = getParameterReferenceCount(parameterId);
  //
  // parameterReferenceCountMap.put(parameterId, ++count);
  // }
  //
  // public void decrementParameterReferenceCount(@NonNull String parameterId) {
  // int count = getParameterReferenceCount(parameterId);
  //
  // if (count == 0) {
  // throw new IllegalStateException(String.format("reference count for parameter '%s' is already 0",
  // parameterId));
  // } else {
  // count -= 1;
  // if (count > 0) {
  // parameterReferenceCountMap.put(parameterId, count);
  // } else {
  // parameterReferenceCountMap.remove(parameterId);
  // }
  // }
  // }

  private static class ItemGroup {
    @NonNull
    private final ItemType itemType;
    Map<String, EntityItem> idToEntityMap;

    public ItemGroup(@NonNull ItemType itemType) {
      this.itemType = itemType;
      this.idToEntityMap = new LinkedHashMap<>();
    }

    public EntityItem getEntity(@NonNull String identifier) {
      return idToEntityMap.get(identifier);
    }

    @NonNull
    public Collection<EntityItem> getEntities() {
      return idToEntityMap.values();
    }

    public EntityItem add(@NonNull EntityItem entity) {
      assert itemType.equals(entity.getItemType());
      return idToEntityMap.put(entity.getOriginalIdentifier(), entity);
    }
  }
}
