package model.commands

import com.gu.media.logging.Logging
import com.gu.pandomainauth.model.{User => PandaUser}
import data.DataStores
import model.{Audit, MediaAtom}
import util.AWSConfig

class AddPlutoProjectCommand(atomId: String, plutoId: String, override val stores: DataStores, user: PandaUser,
                            awsConfig: AWSConfig)

  extends Command with Logging {

    override type T = MediaAtom

    override def process(): (MediaAtom, Audit) = {

      val (updatedAtom, audit) = new SetPlutoIdCommand(atomId, plutoId, stores, user).process()

      stores.pluto.getUploadsWithAtomId(atomId).map(upload =>
        awsConfig.sendOnKinesis(awsConfig.uploadsStreamName, upload.s3Key, upload)
      )

      (updatedAtom, audit)
    }
  }
