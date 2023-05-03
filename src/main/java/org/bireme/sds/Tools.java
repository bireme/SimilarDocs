/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sds;

import java.io.File;


/**
 *
 * author Heitor Barbieri
 * date: 20170113
 */
public class Tools {
    public static boolean deleteLockFile(final String indexPath) {
        return new File(indexPath, "write.lock").delete();
    }
}
