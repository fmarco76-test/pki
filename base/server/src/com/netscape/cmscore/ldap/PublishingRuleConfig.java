//
// Copyright Red Hat, Inc.
//
// SPDX-License-Identifier: GPL-2.0-or-later
//
package com.netscape.cmscore.ldap;

import com.netscape.cmscore.base.ConfigStorage;
import com.netscape.cmscore.base.PropConfigStore;
import com.netscape.cmscore.base.SimpleProperties;

public class PublishingRuleConfig extends PropConfigStore {

    public PublishingRuleConfig() {
    }

    public PublishingRuleConfig(ConfigStorage storage) {
        super(storage);
    }

    public PublishingRuleConfig(String name, SimpleProperties source) {
        super(name, source);
    }
}
