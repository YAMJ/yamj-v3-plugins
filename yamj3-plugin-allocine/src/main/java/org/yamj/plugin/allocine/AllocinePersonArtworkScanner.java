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

import com.moviejukebox.allocine.model.PersonInfos;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.artwork.PersonArtworkScanner;
import org.yamj.plugin.api.metadata.IPerson;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class AllocinePersonArtworkScanner extends AbstractAllocineScanner implements PersonArtworkScanner {

    private static final Logger LOG = LoggerFactory.getLogger(AllocinePersonArtworkScanner.class);

    @Override
    public List<ArtworkDTO> getPhotos(IPerson person) {
        String allocineId = getPersonId(person, false);
        if (isNoValidAllocineId(allocineId)) {
            LOG.debug("Allocine id not available '{}'", person.getName());
            return Collections.emptyList();
        }
        
        PersonInfos personInfos = allocineApiWrapper.getPersonInfos(allocineId, false);
        if (personInfos == null || personInfos.isNotValid() || StringUtils.isBlank(personInfos.getPhotoURL())) {
            return Collections.emptyList();
        }

        ArtworkDTO dto = new ArtworkDTO(getScannerName(), personInfos.getPhotoURL(), allocineId);
        return Collections.singletonList(dto);
    }
}
