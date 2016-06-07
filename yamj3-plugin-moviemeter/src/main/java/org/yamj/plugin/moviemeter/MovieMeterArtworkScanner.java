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
package org.yamj.plugin.moviemeter;

import static org.yamj.plugin.moviemeter.MovieMeterPlugin.SCANNER_NAME;

import com.omertron.moviemeter.model.FilmInfo;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.artwork.MovieArtworkScanner;
import org.yamj.plugin.api.model.IMovie;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class MovieMeterArtworkScanner extends AbstractMovieMeterScanner implements MovieArtworkScanner {

    private static final Logger LOG = LoggerFactory.getLogger(MovieMeterArtworkScanner.class);

    @Override
    public List<ArtworkDTO> getPosters(IMovie movie) {
        final String movieMeterId = getMovieId(movie, false);
        if (!isValidMovieId(movieMeterId)) {
            LOG.debug("MovieMeter id not available '{}'", movie.getTitle());
            return null;
        }

        FilmInfo filmInfo = this.movieMeterApiWrapper.getFilmInfo(movieMeterId,false);
        if (filmInfo == null || filmInfo.getPosters() == null || filmInfo.getPosters().getLarge() == null) {
            LOG.debug("No MovieMeter poster URL for movie: {}", movieMeterId);
            return null;
        }
        
        ArtworkDTO dto = new ArtworkDTO(SCANNER_NAME, filmInfo.getPosters().getLarge(), movieMeterId);
        return Collections.singletonList(dto);
    }

    @Override
    public List<ArtworkDTO> getFanarts(IMovie movie) {
        return null;
    }
}
