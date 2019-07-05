import com.navis.argo.ContextHelper
import com.navis.argo.EdiCarrierVisit
import com.navis.argo.EdiContainer
import com.navis.argo.EdiOperator
import com.navis.argo.EdiRailCar
import com.navis.argo.EdiRailCarVisit
import com.navis.argo.EdiTrainVisit
import com.navis.argo.PreadviseTransactionDocument
import com.navis.argo.PreadviseTransactionsDocument
import com.navis.argo.RailRoad
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.LockTypeEnum
import com.navis.argo.business.reference.Container
import com.navis.argo.business.reference.Equipment
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.util.BizViolation
import com.navis.inventory.InventoryPropertyKeys
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * Created by HOME on 04/09/2018.
 */
class DPWPR322EdiPostInterceptor extends AbstractEdiPostInterceptor {

    private static final Logger LOGGER = Logger.getLogger(this.class);

    void beforeEdiPost(org.apache.xmlbeans.XmlObject inXmlTransactionDocument, Map inParams) {

        LOGGER.setLevel(Level.DEBUG);
        LOGGER.info("DPWPR322EdiPostInterceptor started execution")

        PreadviseTransactionsDocument preadviseTransactionsDocument = (PreadviseTransactionsDocument) inXmlTransactionDocument;
        PreadviseTransactionsDocument.PreadviseTransactions preadviseTransactions = preadviseTransactionsDocument.getPreadviseTransactions();
        PreadviseTransactionDocument.PreadviseTransaction[] arrayOfTransactions = preadviseTransactions.getPreadviseTransactionArray();
        PreadviseTransactionDocument.PreadviseTransaction preadviseTransaction = arrayOfTransactions[0];

        EdiContainer ediContainer = preadviseTransaction.getEdiContainer()
        LOGGER.debug("Inside before DPWPR322InboundVisit :: " + ediContainer.getContainerCategory())
        Container container = Container.findContainer(ediContainer.getContainerNbr());
        EdiOperator ediOperator = ediContainer.getContainerOperator();

        EdiCarrierVisit ediCarrierVisit=preadviseTransaction.getEdiInboundVisit()
        EdiTrainVisit ediTrainVisit= ediCarrierVisit.getEdiTrainVisit()
        RailRoad railRoad= ediTrainVisit.getTrainOperator()
        String railRoadCode=railRoad.getRailRoadCode()

        EdiRailCarVisit ediRailCarVisit= ediCarrierVisit.getEdiRailCarVisit()
        EdiRailCar ediRailCar= ediRailCarVisit.getEdiRailCar()
        String railCarId= ediRailCar.getRailCarId()

        if (container != null) {
            if (ediOperator == null) {
                throw BizViolation.create(InventoryPropertyKeys.CONTAINER_OPERATOR_REQUIRED, null);
            }

            UnitFinder unitFinder = (UnitFinder) Roastery.getBean("unitFinder")
            Equipment equipment = Equipment.findEquipment(ediContainer.getContainerNbr())
            LOGGER.debug("Equipment :" + equipment)
            if (equipment!=null) {
                Unit unit = unitFinder.findAttachedUnit(ContextHelper.getThreadComplex(), equipment)
                if (unit.getUnitFlexString04() != null) {
                    ediTrainVisit.setTrainId(unit.getUnitActiveUfvNowActive().getUfvActualIbCv().getCvId())
                    if(LocTypeEnum.RAILCAR.equals(unit.getUnitActiveUfvNowActive().getUfvLastKnownPosition().getPosLocType())){
                        ediRailCar.setRailCarId(unit.getUnitActiveUfvNowActive().getUfvLastKnownPosition().getPosLocId())

                    }
                }
                LOGGER.debug("In After EDI post Unit Declared IBCV"+unit.getUnitDeclaredIbCv())

                LOGGER.debug("In After EDI post Train ID"+unit.getUnitDeclaredIbCv().getCarrierIbVoyNbrOrTrainId())

            }
        }
    }
}
