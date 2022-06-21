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

import gov.nist.secauto.metaschema.model.common.util.ObjectUtils;
import gov.nist.secauto.oscal.lib.model.IncludeAll;
import gov.nist.secauto.oscal.lib.model.ProfileImport;
import gov.nist.secauto.oscal.lib.model.control.catalog.IControl;
import gov.nist.secauto.oscal.lib.model.control.profile.IProfileSelectControlById;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface IControlFilter {
  @NotNull
  IControlFilter ALWAYS_MATCH = new IControlFilter() {
    @Override
    public @NotNull Pair<@NotNull Boolean, @NotNull Boolean> match(@NotNull IControl control, boolean defaultMatch) {
      return IControlSelectionFilter.MATCH;
    }

    @Override
    public @NotNull IControlSelectionFilter getInclusionFilter() {
      return IControlSelectionFilter.ALL_MATCH;
    }

    @Override
    public @NotNull IControlSelectionFilter getExclusionFilter() {
      return IControlSelectionFilter.NONE_MATCH;
    }
  };

  @NotNull
  IControlFilter NONE_MATCH = new IControlFilter() {

    @Override
    public @NotNull Pair<@NotNull Boolean, @NotNull Boolean> match(@NotNull IControl control, boolean defaultMatch) {
      return IControlSelectionFilter.NON_MATCH;
    }

    @Override
    public @NotNull IControlSelectionFilter getInclusionFilter() {
      return IControlSelectionFilter.NONE_MATCH;
    }

    @Override
    public @NotNull IControlSelectionFilter getExclusionFilter() {
      return IControlSelectionFilter.NONE_MATCH;
    }
  };

  /**
   * Construct a new filter instance based on the provided profile import statement.
   * 
   * @param profileImport
   *          an OSCAL profile import statement
   * @return a new control filter
   */
  @NotNull
  static IControlFilter newInstance(@NotNull ProfileImport profileImport) {
    return new Filter(profileImport);
  }

  @NotNull
  static IControlFilter newInstance(@NotNull IControlSelectionFilter includes,
      @NotNull IControlSelectionFilter excludes) {
    return new Filter(includes, excludes);
  }

  /**
   * Determines if the control is matched by this filter. This method returns a {@link Pair} where the
   * first member of the pair indicates if the control matches, and the second indicates if the match
   * applies to child controls as well.
   * 
   * @param control
   *          the control to check for a match
   * @return a pair indicating the status of the match ({@code true} for a match or {@code false}
   *         otherwise), and if a match applies to child controls
   */
  @NotNull
  default Pair<@NotNull Boolean, @NotNull Boolean> match(@NotNull IControl control) {
    return match(control, false);
  }

  /**
   * Determines if the control is matched by this filter. This method returns a {@link Pair} where the
   * first member of the pair indicates if the control matches, and the second indicates if the match
   * applies to child controls as well.
   * 
   * @param control
   *          the control to check for a match
   * @param defaultMatch
   *          the match status to use if the filter doesn't have an explicit hit
   * @return a pair indicating the status of the match ({@code true} for a match or {@code false}
   *         otherwise), and if a match applies to child controls
   */
  @NotNull
  Pair<@NotNull Boolean, @NotNull Boolean> match(@NotNull IControl control, boolean defaultMatch);

  @NotNull
  IControlSelectionFilter getInclusionFilter();

  @NotNull
  IControlSelectionFilter getExclusionFilter();

  class Filter implements IControlFilter {
    @NotNull
    private final IControlSelectionFilter inclusionFilter;
    @NotNull
    private final IControlSelectionFilter exclusionFilter;

    public Filter(@NotNull ProfileImport profileImport) {
      IncludeAll includeAll = profileImport.getIncludeAll();

      if (includeAll == null) {
        List<? extends IProfileSelectControlById> selections = profileImport.getIncludeControls();
        if (selections == null) {
          this.inclusionFilter = IControlSelectionFilter.NONE_MATCH;
        } else {
          this.inclusionFilter = new DefaultControlSelectionFilter(selections);
        }
      } else {
        this.inclusionFilter = IControlSelectionFilter.ALL_MATCH;
      }

      List<? extends IProfileSelectControlById> selections = profileImport.getExcludeControls();
      if (selections == null) {
        this.exclusionFilter = IControlSelectionFilter.NONE_MATCH;
      } else {
        this.exclusionFilter = new DefaultControlSelectionFilter(selections);
      }

    }

    public Filter(@NotNull IControlSelectionFilter includes, @NotNull IControlSelectionFilter excludes) {
      this.inclusionFilter = includes;
      this.exclusionFilter = excludes;
    }

    @Override
    @NotNull
    public IControlSelectionFilter getInclusionFilter() {
      return inclusionFilter;
    }

    @Override
    @NotNull
    public IControlSelectionFilter getExclusionFilter() {
      return exclusionFilter;
    }

    @Override
    public Pair<@NotNull Boolean, @NotNull Boolean> match(@NotNull IControl control, boolean defaultMatch) {
      @NotNull
      Pair<@NotNull Boolean, @NotNull Boolean> result = getInclusionFilter().apply(control);
      boolean left = ObjectUtils.notNull(result.getLeft());
      if (left) {
        // this is a positive include match. Is it excluded?
        Pair<@NotNull Boolean, @NotNull Boolean> excluded = getExclusionFilter().apply(control);
        if (ObjectUtils.notNull(excluded.getLeft())) {
          // the effective result is a non-match
          result = IControlSelectionFilter.NON_MATCH;
        }
      } else {
        result = defaultMatch ? IControlSelectionFilter.MATCH : IControlSelectionFilter.NON_MATCH;
      }
      return result;
    }

  }

}
