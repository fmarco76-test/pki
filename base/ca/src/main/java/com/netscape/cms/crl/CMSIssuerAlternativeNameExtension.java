// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.cms.crl;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.dogtagpki.server.ca.ICMSCRLExtension;
import org.mozilla.jss.netscape.security.util.DerValue;
import org.mozilla.jss.netscape.security.util.ObjectIdentifier;
import org.mozilla.jss.netscape.security.util.Utils;
import org.mozilla.jss.netscape.security.x509.DNSName;
import org.mozilla.jss.netscape.security.x509.EDIPartyName;
import org.mozilla.jss.netscape.security.x509.Extension;
import org.mozilla.jss.netscape.security.x509.GeneralName;
import org.mozilla.jss.netscape.security.x509.GeneralNames;
import org.mozilla.jss.netscape.security.x509.IPAddressName;
import org.mozilla.jss.netscape.security.x509.IssuerAlternativeNameExtension;
import org.mozilla.jss.netscape.security.x509.OIDName;
import org.mozilla.jss.netscape.security.x509.PKIXExtensions;
import org.mozilla.jss.netscape.security.x509.RFC822Name;
import org.mozilla.jss.netscape.security.x509.URIName;
import org.mozilla.jss.netscape.security.x509.X500Name;

import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.EPropertyNotFound;
import com.netscape.certsrv.base.IExtendedPluginInfo;
import com.netscape.certsrv.common.NameValuePairs;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.base.ConfigStore;

/**
 * This represents a issuer alternative name extension.
 *
 * @version $Revision$, $Date$
 */
public class CMSIssuerAlternativeNameExtension
        implements ICMSCRLExtension, IExtendedPluginInfo {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CMSIssuerAlternativeNameExtension.class);

    private static final String PROP_RFC822_NAME = "rfc822Name";
    private static final String PROP_DNS_NAME = "dNSName";
    private static final String PROP_DIR_NAME = "directoryName";
    private static final String PROP_EDI_NAME = "ediPartyName";
    private static final String PROP_URI_NAME = "URI";
    private static final String PROP_IP_NAME = "iPAddress";
    private static final String PROP_OID_NAME = "OID";
    private static final String PROP_OTHER_NAME = "otherName";

    public CMSIssuerAlternativeNameExtension() {
    }

    @Override
    public Extension setCRLExtensionCriticality(Extension ext,
            boolean critical) {
        IssuerAlternativeNameExtension issuerAltNameExt = null;
        GeneralNames names = null;

        try {
            names = (GeneralNames) ((IssuerAlternativeNameExtension) ext)
                    .get(IssuerAlternativeNameExtension.ISSUER_NAME);
            issuerAltNameExt = new IssuerAlternativeNameExtension(Boolean.valueOf(critical), names);
        } catch (IOException e) {
            logger.warn(CMS.getLogMessage("CRL_CREATE_ISSUER_ALT_NAME_EXT", e.toString()), e);
        }

        return issuerAltNameExt;
    }

    @Override
    public Extension getCRLExtension(ConfigStore config, Object ip, boolean critical) {
        IssuerAlternativeNameExtension issuerAltNameExt = null;
        int numNames = 0;

        try {
            numNames = config.getInteger("numNames", 0);
        } catch (EBaseException e) {
            logger.warn(CMS.getLogMessage("CRL_CREATE_ISSUER_INVALID_NUM_NAMES", e.toString()), e);
        }

        if (numNames > 0) {
            GeneralNames names = new GeneralNames();

            for (int i = 0; i < numNames; i++) {
                String nameType = null;

                try {
                    nameType = config.getString("nameType" + i);

                } catch (EPropertyNotFound e) {
                    logger.warn(CMS.getLogMessage("CRL_CREATE_ISSUER_UNDEFINED_TYPE", Integer.toString(i), e.toString()), e);

                } catch (EBaseException e) {
                    logger.warn(CMS.getLogMessage("CRL_CREATE_ISSUER_INVALID_TYPE", Integer.toString(i), e.toString()), e);
                }

                if (nameType != null && nameType.length() > 0) {
                    String name = null;

                    try {
                        name = config.getString("name" + i);

                    } catch (EPropertyNotFound e) {
                        logger.warn(CMS.getLogMessage("CRL_CREATE_ISSUER_UNDEFINED_TYPE",
                                Integer.toString(i), e.toString()), e);

                    } catch (EBaseException e) {
                        logger.warn(CMS.getLogMessage("CRL_CREATE_ISSUER_INVALID_TYPE", Integer.toString(i), e.toString()), e);
                    }

                    if (name != null && name.length() > 0) {
                        if (nameType.equalsIgnoreCase(PROP_DIR_NAME)) {
                            try {
                                X500Name dirName = new X500Name(name);

                                names.addElement(dirName);
                            } catch (IOException e) {
                                logger.warn(CMS.getLogMessage("CRL_CREATE_INVALID_500NAME", e.toString()), e);
                            }
                        } else if (nameType.equalsIgnoreCase(PROP_RFC822_NAME)) {
                            RFC822Name rfc822Name = new RFC822Name(name);

                            names.addElement(rfc822Name);
                        } else if (nameType.equalsIgnoreCase(PROP_DNS_NAME)) {
                            DNSName dnsName = new DNSName(name);

                            names.addElement(dnsName);
                        } else if (nameType.equalsIgnoreCase(PROP_EDI_NAME)) {
                            EDIPartyName ediName = new EDIPartyName(name);

                            names.addElement(ediName);
                        } else if (nameType.equalsIgnoreCase(PROP_URI_NAME)) {
                            URIName uriName = new URIName(name);

                            names.addElement(uriName);
                        } else if (nameType.equalsIgnoreCase(PROP_IP_NAME)) {
                            IPAddressName ipName = new IPAddressName(name);

                            names.addElement(ipName);
                        } else if (nameType.equalsIgnoreCase(PROP_OID_NAME)) {
                            ObjectIdentifier oid = new ObjectIdentifier(name);
                            OIDName oidNmae = new OIDName(oid);

                            names.addElement(oidNmae);
                        } else if (nameType.equalsIgnoreCase(PROP_OTHER_NAME)) {

                            try {
                                byte[] val = Utils.base64decode(name);
                                DerValue derVal = new DerValue(new ByteArrayInputStream(val));
                                GeneralName generalName = new GeneralName(derVal);

                                names.addElement(generalName);

                            } catch (IOException e) {
                                logger.warn(CMS.getLogMessage("CRL_INVALID_OTHER_NAME", e.toString()), e);
                            }
                        } else {
                            logger.warn(CMS.getLogMessage("CRL_CREATE_ISSUER_INVALID_TYPE", nameType, ""));
                        }
                    }
                }
            }

            if (names.size() > 0) {
                try {
                    issuerAltNameExt = new IssuerAlternativeNameExtension(
                                Boolean.valueOf(critical), names);
                } catch (IOException e) {
                    logger.warn(CMS.getLogMessage("CRL_CREATE_ISSUER_ALT_NAME_EXT", e.toString()), e);
                }
            }
        }

        return issuerAltNameExt;
    }

    @Override
    public String getCRLExtOID() {
        return PKIXExtensions.IssuerAlternativeName_Id.toString();
    }

    @Override
    public void getConfigParams(ConfigStore config, NameValuePairs nvp) {
        int numNames = 0;

        try {
            numNames = config.getInteger("numNames", 0);
        } catch (EBaseException e) {
            logger.warn("Invalid numNames property for CRL IssuerAlternativeName extension: " + e.getMessage(), e);
        }

        nvp.put("numNames", String.valueOf(numNames));

        for (int i = 0; i < numNames; i++) {
            String nameType = null;

            try {
                nameType = config.getString("nameType" + i);

            } catch (EPropertyNotFound e) {
                logger.warn("Undefined nameType" + i + " property for CRL IssuerAlternativeName extension: " + e.getMessage(), e);

            } catch (EBaseException e) {
                logger.warn("Invalid nameType" + i + " property for CRL IssuerAlternativeName extension: " + e.getMessage(), e);
            }

            if (nameType != null && nameType.length() > 0) {
                nvp.put("nameType" + i, nameType);
            } else {
                nvp.put("nameType" + i, "");
            }

            String name = null;

            try {
                name = config.getString("name" + i);

            } catch (EPropertyNotFound e) {
                logger.warn("Undefined name" + i + " property for CRL IssuerAlternativeName extension: " + e.getMessage(), e);

            } catch (EBaseException e) {
                logger.warn("Invalid name" + i + " property for CRL IssuerAlternativeName extension: " + e.getMessage(), e);
            }

            if (name != null && name.length() > 0) {
                nvp.put("name" + i, name);
            } else {
                nvp.put("name" + i, "");
            }
        }

        if (numNames < 3) {
            for (int i = numNames; i < 3; i++) {
                nvp.put("nameType" + i, "");
                nvp.put("name" + i, "");
            }
        }
    }

    @Override
    public String[] getExtendedPluginInfo() {
        String[] params = {
                //"type;choice(CRLExtension,CRLEntryExtension);"+
                //"CRL Extension type. This field is not editable.",
                "enable;boolean;Check to enable Issuer Alternative Name CRL extension.",
                "critical;boolean;Set criticality for Issuer Alternative Name CRL extension.",
                "numNames;number;Set number of alternative names for the CRL issuer.",
                "nameType0;choice(" + PROP_RFC822_NAME + "," + PROP_DIR_NAME + "," + PROP_DNS_NAME + "," +
                        PROP_EDI_NAME + "," + PROP_URI_NAME + "," + PROP_IP_NAME + "," + PROP_OID_NAME + "," +
                        PROP_OTHER_NAME + ");Select Issuer Alternative Name type.",
                "name0;string;Enter Issuer Alternative Name corresponding to the selected name type.",
                "nameType1;choice(" + PROP_RFC822_NAME + "," + PROP_DIR_NAME + "," + PROP_DNS_NAME + "," +
                        PROP_EDI_NAME + "," + PROP_URI_NAME + "," + PROP_IP_NAME + "," + PROP_OID_NAME + "," +
                        PROP_OTHER_NAME + ");Select Issuer Alternative Name type.",
                "name1;string;Enter Issuer Alternative Name corresponding to the selected name type.",
                "nameType2;choice(" + PROP_RFC822_NAME + "," + PROP_DIR_NAME + "," + PROP_DNS_NAME + "," +
                        PROP_EDI_NAME + "," + PROP_URI_NAME + "," + PROP_IP_NAME + "," + PROP_OID_NAME + "," +
                        PROP_OTHER_NAME + ");Select Issuer Alternative Name type.",
                "name2;string;Enter Issuer Alternative Name corresponding to the selected name type.",
                IExtendedPluginInfo.HELP_TOKEN +
                        ";configuration-ca-edit-crlextension-issueralternativename",
                IExtendedPluginInfo.HELP_TEXT +
                        ";The issuer alternative names extension allows additional" +
                        " identities to be associated with the issuer of the CRL."
            };

        return params;
    }
}
