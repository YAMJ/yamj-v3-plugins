/*
 *      Copyright (c) 2004-2016 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ) plugins.
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3-plugins
 *
 */
package org.yamj.plugin.allocine;

import static org.yamj.plugin.allocine.AllocinePlugin.SCANNER_NAME;

import com.moviejukebox.allocine.model.PersonInfos;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.PersonDTO;
import org.yamj.plugin.api.metadata.PersonScanner;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class AllocinePersonScanner extends AbstractAllocineScanner implements PersonScanner {

    private static final Logger LOG = LoggerFactory.getLogger(AllocinePersonScanner.class);

    @Override
    public boolean scanPerson(PersonDTO person, boolean throwTempError) {
        final String allocineId = person.getIds().get(SCANNER_NAME);
        if (StringUtils.isBlank(allocineId)) {
            return false;
        }

        // get person info
        PersonInfos personInfos = allocineApiWrapper.getPersonInfos(allocineId, throwTempError);
        if (personInfos == null || personInfos.isNotValid()) {
            LOG.error("Can't find informations for person '{}'", person.getName());
            return false;
        }
        
        // fill in data
        person.setName(personInfos.getFullName())
            .setFirstName(personInfos.getFirstName())
            .setLastName(personInfos.getLastName())
            .setBirthDay(MetadataTools.parseToDate(personInfos.getBirthDate()))
            .setBirthPlace(personInfos.getBirthPlace())
            .setBirthName(personInfos.getRealName())
            .setDeathDay(MetadataTools.parseToDate(personInfos.getDeathDate()))
            .setDeathPlace(personInfos.getDeathPlace())
            .setBiography(personInfos.getBiography());
        
        return true;
    }
}
