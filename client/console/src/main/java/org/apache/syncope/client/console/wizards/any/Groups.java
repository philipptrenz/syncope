/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.client.console.wizards.any;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.jaxrs.ext.search.client.CompleteCondition;
import org.apache.syncope.client.console.SyncopeConsoleApplication;
import org.apache.syncope.client.console.rest.DynRealmRestClient;
import org.apache.syncope.client.console.rest.GroupRestClient;
import org.apache.syncope.client.console.wicket.ajax.markup.html.LabelInfo;
import org.apache.syncope.client.console.wicket.markup.html.form.AjaxPalettePanel;
import org.apache.syncope.client.lib.SyncopeClient;
import org.apache.syncope.common.lib.EntityTOUtils;
import org.apache.syncope.common.lib.SyncopeConstants;
import org.apache.syncope.common.lib.search.GroupFiqlSearchConditionBuilder;
import org.apache.syncope.common.lib.to.AnyTO;
import org.apache.syncope.common.lib.to.DynRealmTO;
import org.apache.syncope.common.lib.to.GroupTO;
import org.apache.syncope.common.lib.to.MembershipTO;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.extensions.wizard.WizardStep;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.util.ListModel;
import org.apache.syncope.common.lib.to.GroupableRelatableTO;
import org.apache.wicket.authroles.authorization.strategies.role.metadata.ActionPermissions;
import org.apache.wicket.authroles.authorization.strategies.role.metadata.MetaDataRoleAuthorizationStrategy;
import org.apache.wicket.extensions.wizard.WizardModel.ICondition;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.PropertyModel;

public class Groups extends WizardStep implements ICondition {

    private static final long serialVersionUID = 552437609667518888L;

    private static final int MAX_GROUP_LIST_CARDINALITY = 30;

    private final GroupRestClient groupRestClient = new GroupRestClient();

    private final List<DynRealmTO> allDynRealms = new DynRealmRestClient().list();

    private GroupsModel groupsModel;

    private final AnyTO anyTO;

    private boolean templateMode;

    protected WebMarkupContainer dyngroupsContainer;

    protected WebMarkupContainer dynrealmsContainer;

    public <T extends AnyTO> Groups(final AnyWrapper<T> modelObject, final boolean templateMode) {
        super();
        this.templateMode = templateMode;

        this.anyTO = modelObject.getInnerObject();

        groupsModel = new GroupsModel();

        // -----------------------------------------------------------------
        // Pre-Authorizations
        // -----------------------------------------------------------------
        ActionPermissions permissions = new ActionPermissions();
        setMetaData(MetaDataRoleAuthorizationStrategy.ACTION_PERMISSIONS, permissions);
        permissions.authorizeAll(RENDER);
        // -----------------------------------------------------------------

        setOutputMarkupId(true);

        WebMarkupContainer groupsContainer = new WebMarkupContainer("groupsContainer");
        groupsContainer.setOutputMarkupId(true);
        groupsContainer.setOutputMarkupPlaceholderTag(true);
        add(groupsContainer);

        dyngroupsContainer = new WebMarkupContainer("dyngroupsContainer");
        dyngroupsContainer.setOutputMarkupId(true);
        dyngroupsContainer.setOutputMarkupPlaceholderTag(true);
        add(dyngroupsContainer);

        if (anyTO instanceof GroupTO) {
            groupsContainer.add(new Label("groups").setVisible(false));
            groupsContainer.setVisible(false);
            dyngroupsContainer.add(new Label("dyngroups").setVisible(false));
            dyngroupsContainer.setVisible(false);
        } else {
            AjaxPalettePanel.Builder<MembershipTO> builder = new AjaxPalettePanel.Builder<MembershipTO>().
                    setRenderer(new IChoiceRenderer<MembershipTO>() {

                        private static final long serialVersionUID = -3086661086073628855L;

                        @Override
                        public Object getDisplayValue(final MembershipTO object) {
                            return object.getGroupName();
                        }

                        @Override
                        public String getIdValue(final MembershipTO object, final int index) {
                            return object.getGroupName();
                        }

                        @Override
                        public MembershipTO getObject(
                                final String id, final IModel<? extends List<? extends MembershipTO>> choices) {

                            return IterableUtils.find(choices.getObject(), new Predicate<MembershipTO>() {

                                @Override
                                public boolean evaluate(final MembershipTO object) {
                                    return id.equalsIgnoreCase(object.getGroupName());
                                }
                            });
                        }
                    });

            groupsContainer.add(builder.setAllowOrder(true).withFilter().build("groups",
                    new ListModel<MembershipTO>() {

                private static final long serialVersionUID = -2583290457773357445L;

                @Override
                public List<MembershipTO> getObject() {
                    return Groups.this.groupsModel.getMemberships();
                }

            }, new AjaxPalettePanel.Builder.Query<MembershipTO>() {

                private static final long serialVersionUID = -7223078772249308813L;

                @Override
                public List<MembershipTO> execute(final String filter) {
                    return CollectionUtils.collect(
                            StringUtils.isEmpty(filter) || "*".equals(filter)
                            ? groupsModel.getObject()
                            : groupRestClient.search(
                                    anyTO.getRealm(),
                                    SyncopeClient.getGroupSearchConditionBuilder().
                                            isAssignable().and().is("name").equalTo(filter).query(),
                                    1, MAX_GROUP_LIST_CARDINALITY,
                                    new SortParam<>("name", true),
                                    null),
                            new Transformer<GroupTO, MembershipTO>() {

                        @Override
                        public MembershipTO transform(final GroupTO input) {
                            return new MembershipTO.Builder().
                                    group(input.getKey(), input.getName()).
                                    build();
                        }
                    }, new ArrayList<MembershipTO>());
                }
            }).hideLabel().setOutputMarkupId(true));

            dyngroupsContainer.add(new AjaxPalettePanel.Builder<String>().setAllowOrder(true).build("dyngroups",
                    new ListModel<String>() {

                private static final long serialVersionUID = -2583290457773357445L;

                @Override
                public List<String> getObject() {
                    return Groups.this.groupsModel.getDynMemberships();
                }

            }, new ListModel<>(CollectionUtils.collect(groupsModel.getObject(),
                            new Transformer<GroupTO, String>() {

                        @Override
                        public String transform(final GroupTO input) {
                            return input.getName();
                        }
                    }, new ArrayList<String>()))).
                    hideLabel().setEnabled(false).setOutputMarkupId(true));

            // ---------------------------------
        }

        dynrealmsContainer = new WebMarkupContainer("dynrealmsContainer");
        dynrealmsContainer.setOutputMarkupId(true);
        dynrealmsContainer.setOutputMarkupPlaceholderTag(true);
        dynrealmsContainer.add(new AjaxPalettePanel.Builder<String>().build("dynrealms",
                new PropertyModel<List<String>>(anyTO, "dynRealms"),
                new ListModel<>(
                        CollectionUtils.collect(allDynRealms,
                                EntityTOUtils.keyTransformer(),
                                new ArrayList<String>()))).
                hideLabel().setEnabled(false).setOutputMarkupId(true));
        add(dynrealmsContainer);

        // ------------------
        // insert changed label if needed
        // ------------------
        if (modelObject instanceof UserWrapper
                && UserWrapper.class.cast(modelObject).getPreviousUserTO() != null
                && !ListUtils.isEqualList(
                        UserWrapper.class.cast(modelObject).getInnerObject().getMemberships(),
                        UserWrapper.class.cast(modelObject).getPreviousUserTO().getMemberships())) {
            groupsContainer.add(new LabelInfo("changed", StringUtils.EMPTY));
        } else {
            groupsContainer.add(new Label("changed", StringUtils.EMPTY));
        }
        // ------------------
    }

    @Override
    public boolean evaluate() {
        return ((anyTO instanceof GroupTO)
                ? CollectionUtils.isNotEmpty(allDynRealms)
                : CollectionUtils.isNotEmpty(allDynRealms) || CollectionUtils.isNotEmpty(groupsModel.getObject()))
                && SyncopeConsoleApplication.get().getSecuritySettings().getAuthorizationStrategy().
                        isActionAuthorized(this, RENDER);
    }

    private class GroupsModel extends ListModel<GroupTO> {

        private static final long serialVersionUID = -4541954630939063927L;

        private List<GroupTO> groups;

        private List<MembershipTO> memberships;

        private List<String> dynMemberships;

        private String realm;

        @Override
        public List<GroupTO> getObject() {
            reload();
            return groups;
        }

        /**
         * Retrieve the first MAX_GROUP_LIST_CARDINALITY assignable.
         */
        private void reloadObject() {
            groups = groupRestClient.search(
                    realm,
                    SyncopeClient.getGroupSearchConditionBuilder().isAssignable().query(),
                    1,
                    MAX_GROUP_LIST_CARDINALITY,
                    new SortParam<>("name", true),
                    null);
        }

        public List<MembershipTO> getMemberships() {
            reload();
            return memberships;
        }

        /**
         * Retrieve group memberships.
         */
        private void reloadMemberships() {
            // this is to be sure to have group names (required to see membership details in approval page)
            GroupFiqlSearchConditionBuilder searchConditionBuilder = SyncopeClient.getGroupSearchConditionBuilder();

            List<CompleteCondition> conditions = new ArrayList<>();
            for (MembershipTO membershipTO : GroupableRelatableTO.class.cast(anyTO).getMemberships()) {
                conditions.add(searchConditionBuilder.is("key").equalTo(membershipTO.getGroupKey()).wrap());
            }

            Map<String, GroupTO> assignedGroups = new HashMap<>();
            if (!conditions.isEmpty()) {
                for (GroupTO group : groupRestClient.search(
                        realm,
                        searchConditionBuilder.isAssignable().and().or(conditions).query(),
                        -1,
                        -1,
                        new SortParam<>("name", true),
                        null)) {
                    assignedGroups.put(group.getKey(), group);
                }
            }

            // set group names in membership TOs and remove membership not assignable
            List<MembershipTO> toBeRemoved = new ArrayList<>();
            for (MembershipTO membership : GroupableRelatableTO.class.cast(anyTO).getMemberships()) {
                if (assignedGroups.containsKey(membership.getGroupKey())) {
                    membership.setGroupName(assignedGroups.get(membership.getGroupKey()).getName());
                } else {
                    toBeRemoved.add(membership);
                }
            }
            GroupableRelatableTO.class.cast(anyTO).getMemberships().removeAll(toBeRemoved);

            memberships = GroupableRelatableTO.class.cast(anyTO).getMemberships();
        }

        public List<String> getDynMemberships() {
            reload();
            return dynMemberships;
        }

        /**
         * Retrieve dyn group memberships.
         */
        private void reloadDynMemberships() {
            GroupFiqlSearchConditionBuilder searchConditionBuilder = SyncopeClient.getGroupSearchConditionBuilder();

            List<CompleteCondition> conditions = new ArrayList<>();
            for (MembershipTO membership : GroupableRelatableTO.class.cast(anyTO).getDynMemberships()) {
                conditions.add(searchConditionBuilder.is("key").equalTo(membership.getGroupKey()).wrap());
            }

            Map<String, GroupTO> assignedGroups = new HashMap<>();
            if (!conditions.isEmpty()) {
                for (GroupTO group : groupRestClient.search(
                        SyncopeConstants.ROOT_REALM,
                        searchConditionBuilder.or(conditions).query(),
                        -1,
                        -1,
                        new SortParam<>("name", true),
                        null)) {
                    assignedGroups.put(group.getKey(), group);
                }
            }

            dynMemberships = CollectionUtils.collect(assignedGroups.values(), new Transformer<GroupTO, String>() {

                @Override
                public String transform(final GroupTO input) {
                    return input.getName();
                }
            }, new ArrayList<String>());
        }

        /**
         * Reload data if the realm changes (see SYNCOPE-1135).
         */
        private void reload() {
            boolean reload;

            if (Groups.this.templateMode) {
                reload = realm == null;
                realm = SyncopeConstants.ROOT_REALM;
            } else {
                reload = !Groups.this.anyTO.getRealm().equalsIgnoreCase(realm);
                realm = Groups.this.anyTO.getRealm();
            }

            if (reload) {
                reloadObject();
                reloadMemberships();
                reloadDynMemberships();
            }
        }
    }
}