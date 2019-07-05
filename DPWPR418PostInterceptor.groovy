import com.navis.argo.*
import com.navis.argo.RailConsistTransactionDocument.RailConsistTransaction.EdiRailCarVisit
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.Facility
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.rail.business.api.RailManager
import com.navis.rail.business.atoms.TrainDirectionEnum
import com.navis.rail.business.entity.*
import com.navis.rail.business.util.RailBizUtil
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * Created by HOME on 27/08/2018.
 */
class DPWPR418PostInterceptor extends AbstractEdiPostInterceptor {
    @Override
    void beforeEdiPost(org.apache.xmlbeans.XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.setLevel(Level.DEBUG);
        LOGGER.info("TrainNameCustomization started execution")
        RailConsistTransactionsDocument consistTransactionsDocument = (RailConsistTransactionsDocument) inXmlTransactionDocument
        RailConsistTransactionsDocument.RailConsistTransactions railConsistTransactions = consistTransactionsDocument.getRailConsistTransactions()
        List<RailConsistTransactionDocument.RailConsistTransaction> railConsistTransactionsList = railConsistTransactions.getRailConsistTransactionList();

        if (railConsistTransactionsList != null && railConsistTransactionsList.size() == 1) {
            EdiTrainVisit ediTrainVisit = railConsistTransactionsList.get(0).getEdiTrainVisit();
            EdiRailCarVisit ediRailCarVisit = railConsistTransactionsList.get(0).getEdiRailCarVisit()
            LOGGER.info("ediRailCarVisit" + ediRailCarVisit)
            String railCarId = ediRailCarVisit.getRailCar().getRailCarId()
            LOGGER.info("railCarId" + railCarId)
            String railCarType = ediRailCarVisit.getRailCar().getRailCarType()
            LOGGER.info("railCarType" + railCarType)
            String railRoadId = ediTrainVisit.getTrainOperator().getRailRoadId()
            LOGGER.info("railRoadId" + railRoadId)

            Railroad railroadBySCACId = findRailroadBySCACId(railRoadId)
            LOGGER.info("railroadBySCACId " + railroadBySCACId)

            LOGGER.info("ediTrainVisit is not null:" + ediTrainVisit)
            if (ediTrainVisit != null) {
                String fcyId = railConsistTransactionsList.get(0).getFacilityId();
                fcyId = fcyId != null ? fcyId : FCY;
                Facility facility = Facility.findFacility(fcyId, ContextHelper.getThreadComplex());
                String trainId = ediTrainVisit.getTrainId();
                LOGGER.info("trainId:" + trainId)
                CarrierVisit carrierVisit = null;
                boolean isActive = false;
                List carrierVisitList = findTrainVisit(ContextHelper.getThreadComplex(), facility, LocTypeEnum.TRAIN, trainId)
                // a b
                LOGGER.info("carrierVisit:" + carrierVisitList)
                if (carrierVisitList != null) { //3
                    for (CarrierVisit cv : carrierVisitList) {
                        if ([CarrierVisitPhaseEnum.WORKING, CarrierVisitPhaseEnum.INBOUND, CarrierVisitPhaseEnum.ARRIVED].contains(cv.getCvVisitPhase())) {
                            carrierVisit = cv;
                            TrainVisitDetails trainVisitDetails = TrainVisitDetails.resolveTvdFromCv(carrierVisit)
                            if (LOCKED.equalsIgnoreCase(trainVisitDetails.rvdtlsFlexString01)) {
                                LOGGER.info("locked state ")
                                inParams.put("SKIP_POSTER", true);
                            } else {
                                LOGGER.info("not locked state ")
                                inParams.put("SKIP_POSTER", false);
                            }
                            isActive = true;
                        } else {
                            if (!isActive)
                                carrierVisit = cv;
                        }
                    }
                    if (!isActive) {
                        LOGGER.info("Train Visit Not Active")
                        inParams.put("SKIP_POSTER", true);
                        StringBuilder trainID = new StringBuilder(trainId)
                        LOGGER.info("trainID" + trainID)
                        Calendar cal = Calendar.getInstance();
                        String currentYear = cal.get(Calendar.YEAR).toString()
                        LOGGER.info("currentYear" + currentYear)
                        int currentMonth = cal.get(Calendar.MONTH).toInteger();
                        LOGGER.info("currentMonth" + currentMonth)
                        String monthStr = monthAlpha(currentMonth);
                        LOGGER.info("monthStr" + monthStr)
                        String yearStr = currentYear.substring(2)
                        LOGGER.info("yearStr" + yearStr)
                        trainID.append(yearStr)
                        LOGGER.info("builder1:" + trainID)
                        trainID.append(monthStr)
                        LOGGER.info("builder2:" + trainID)
                        LOGGER.info("carrierVisit inside else loop:" + carrierVisit)
                        carrierVisit = CarrierVisit.findOrCreateTrainVisit(facility, trainID.toString())
                        TrainVisitDetails visitDetails = TrainVisitDetails.findOrCreateTrainVisit(facility, carrierVisit, railroadBySCACId, null, null, TrainDirectionEnum.INBOUND)
                        LOGGER.info("visitDetails:" + visitDetails)
                        visitDetails.setCvdETA(RailBizUtil.getDate())
                        RailcarType findRailcarType = RailcarType.findRailcarType(railCarType);
                        if (findRailcarType == null) {
                            registerError("RailcarType " + railCarType + " is not available in Master's. Please review.")
                            return;
                        }
                        Railcar railcar = Railcar.findOrCreateRailcar(railCarId, findRailcarType, railroadBySCACId)
                        LOGGER.info("railcar" + railcar)

                        RailManager railManager = (RailManager) Roastery.getBean(RailManager.BEAN_ID)
                        RailcarVisit railcarVisit = railManager.findOrCreateActiveRailcarVisit(visitDetails, null, railcar, null, null, null, null)

                        LOGGER.info("railcarVisit" + railcarVisit)
                        if (visitDetails != null) {
                            HibernateApi.getInstance().save(visitDetails);
                            HibernateApi.getInstance().flush()
                        }
                    }
                } else {
                    carrierVisit = CarrierVisit.findOrCreateTrainVisit(facility, trainId.toString())
                    LOGGER.info("After creating New train visit:" + carrierVisit);
                    TrainVisitDetails visitDetails = TrainVisitDetails.findOrCreateTrainVisit(facility, carrierVisit, railroadBySCACId, null, null, TrainDirectionEnum.INBOUND)
                    if (visitDetails != null) {
                        HibernateApi.getInstance().save(visitDetails);
                        HibernateApi.getInstance().flush()
                    }
                }
            }
        }
    }

    private static String monthAlpha(int inMonth) {
        if (inMonth == null)
            return null;
        if (0 == inMonth)
            return "A"
        else if (1 == inMonth)
            return "B"
        else if (2 == inMonth)
            return "C"
        else if (3 == inMonth)
            return "D"
        else if (4 == inMonth)
            return "E"
        else if (5 == inMonth)
            return "F"
        else if (6 == inMonth)
            return "G"
        else if (7 == inMonth)
            return "H"
        else if (8 == inMonth)
            return "I"
        else if (9 == inMonth)
            return "J"
        else if (10 == inMonth)
            return "K"
        else (11 == inMonth)
        return "L"

    }

    private static Railroad findRailroadBySCACId(String inRailroadId) {
        DomainQuery dq = QueryUtils.createDomainQuery("Railroad").addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_SCAC, inRailroadId));
        return (Railroad) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
    }

    private static List<CarrierVisit> findTrainVisit(Complex inComplex, Facility inFacility, LocTypeEnum inMode, String inCvId) {
        DomainQuery dq = QueryUtils.createDomainQuery("CarrierVisit")
                .addDqPredicate(PredicateFactory.eq(ArgoField.CV_COMPLEX, inComplex.getCpxGkey()))
                .addDqPredicate(PredicateFactory.eq(ArgoField.CV_CARRIER_MODE, inMode))
                .addDqPredicate(PredicateFactory.like(ArgoField.CV_ID, inCvId))
        //.addDqPredicate(PredicateFactory.in(ArgoField.CV_VISIT_PHASE,
        // Arrays.asList([CarrierVisitPhaseEnum.WORKING, CarrierVisitPhaseEnum.INBOUND, CarrierVisitPhaseEnum.ARRIVED])))
        LOGGER.info("carrierVisit inside else loop Doamain Query:" + dq)
        if (inFacility == null) {
            dq.addDqPredicate(PredicateFactory.isNull(ArgoField.CV_FACILITY));
        } else {
            dq.addDqPredicate(PredicateFactory.eq(ArgoField.CV_FACILITY, inFacility.getFcyGkey()));
        }
        return Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
    }

    private static final Logger LOGGER = Logger.getLogger(this.class);
    private String FCY = "FCT"
    private String LOCKED = "LOCKED";
}


