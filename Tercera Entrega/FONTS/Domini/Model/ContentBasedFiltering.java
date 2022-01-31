package Domini.Model;
import java.util.*;

import Domini.ControladorsDomini.CtrlDomini;
import  Domini.DataInterface.FactoriaCtrl;

import java.lang.Math;
import Excepcions.*;
public class ContentBasedFiltering implements Strategy {

    private String nom;
    private int k;
    
    //Pre:k > 0
    //Post: Creadora de la clase ContentBasedFiltering 
    public ContentBasedFiltering (int ka) {
        nom = "ContentBasedFiltering";
        k=ka;
    }

    //Pre:
    //Post: Retorna el nom de l'estrategia de filtering
    public String getNom () {
        return nom;
    }

    //Pre: Existeix l'usuari ua i el map d'items no es buit
    //Post: Retorna un TreeSet<Valoracio> amb les valoracions predites ordenades de mes nota a menys nota
    public  TreeSet<Valoracio> Filtering (Usuari ua, Map<String,Item> items) throws ExcepcionsRecomanador {
        Map <String,Valoracio> ValsUs = ua.getValoracions();
        Vector<Valoracio> MillorsValorats = new Vector<Valoracio>();

        FactoriaCtrl fc = FactoriaCtrl.getInstance(); 
        CtrlDomini DominiCtrl = fc.getCtrlDomini();

        //Recorrem el conjunt de valoracions de l'UsuariBD "ua" i 
        //agafem els items que hagi valorat amb més d'un 60% de la nota maxima
        double maxVal = DominiCtrl.getPuntuacioMaxima();
        for (Map.Entry<String, Valoracio> entry : ValsUs.entrySet()) {
            Valoracio valo1 = entry.getValue(); //obtengo la valoracion
            items.remove(valo1.getItem().getId()); /*Treiem els items valorats de "items" (que son tots els items
            de la base de dades, de forma que "items" i els items valorats de l'usuari son conjunts disjunts) */
            if(valo1.getPuntuacio() >= maxVal*0.6){ 
                MillorsValorats.add(valo1);
            }
        }

        //Per cada item, mirem els k més semblants a aquest i els fiquem a "millorsItems"
        Map <Item, Pair<Integer, Double> >millorsItems = new  HashMap <Item, Pair<Integer, Double> >();
        for(int i = 0; i < MillorsValorats.size(); i++){
            PriorityQueue< Pair<Double,Item> > knear;
            knear = KNearests(MillorsValorats.get(i).getItem(), items, k);
            while (!knear.isEmpty()) {
                Pair<Double,Item> pairIteracioi = knear.poll(); //Desapilem el pair de la nota i el item de la i-essima iteracio
                if (millorsItems.get(pairIteracioi.second)!=null){ //Si el item de la pila de la i-essima iteracio ja estava en el map
                    Pair<Integer, Double> modificar = millorsItems.get(pairIteracioi.second);
                    ++modificar.first; //Sumem el numero de vegades que apareix
                    modificar.second += calcularNota(pairIteracioi.first, MillorsValorats.get(i).getPuntuacio()); //Sumem la nota calculada a la ja present
                    millorsItems.put(pairIteracioi.second, modificar); //Actualitzem les dades al map.
                } else { 
                    Pair<Integer, Double> afegir = new Pair<Integer, Double>();
                    afegir.first = 1; 
                    afegir.second = calcularNota(pairIteracioi.first, MillorsValorats.get(i).getPuntuacio());
                    millorsItems.put(pairIteracioi.second, afegir);
                }
            }
        }

        TreeSet<Valoracio> filtrat = new TreeSet<Valoracio>(new ComparatorValoracio());
        //Recorrem el maps dels millors items i els hi posem una nota pondedara tenint en compte el nombre de cops que es repeteix i la nota
        for (Map.Entry<Item, Pair<Integer, Double> > entry : millorsItems.entrySet()) {
            Double valoracioPonderada = ponderarItem(entry.getValue()); //Ponderem el item iessim
            Valoracio valoracioIteracioi = new Valoracio(valoracioPonderada, entry.getKey(), ua); //Creem la valoracio amb la nota calculada
            valoracioIteracioi.AssignaPredictiva(true); //Assignem que la valoracio es predictiva, ja que l'hem calculat  
            filtrat.add(valoracioIteracioi);  //Afegim el pair al TreeSet
        }
       
        return filtrat;
    }

    //Pre: L'item donat existeix, el map de tots no es buit i k>0
    //Post: Es retornen els k items mes semblants al item donat ordenats de millor a pitjor
    private PriorityQueue< Pair<Double,Item> > KNearests(Item donat, Map<String,Item> tots, int k) throws ExcepcionsRecomanador{
        PriorityQueue< Pair<Double,Item> > queue = new PriorityQueue< Pair<Double,Item> >(new ComparatorPair());
        //Mirem per l'item la similitud amb els items "tots" i els fiquem a "queue" on estaran ordenats de mayor a menor.
        for(Map.Entry<String,Item> entry : tots.entrySet()){
            if(donat.getId() != entry.getValue().getId()){
                double aux = donat.ComparaItem(entry.getValue());
                Pair<Double, Item> act = new Pair<Double, Item>();
                act.first = aux;
                act.second = entry.getValue();
                queue.add(act);
            }
        }
        PriorityQueue< Pair<Double,Item> > knear = new PriorityQueue< Pair<Double,Item> >(new ComparatorPair());
        int cont = 0;
        while (!queue.isEmpty() && cont < k){
            ++cont;
            knear.add(queue.poll());
        }

        return knear;
    }

    //Pre: El pair p no es buit, el primer element de p > 0 i el segon element de p >= 0
    //Post: Es retorna una double en funcio dels elements de la parella
    //Integer es el nombre de vegades que apareix un item, Double es el sumatori de les similituds del item repetit amb el item donat
    private Double ponderarItem(Pair<Integer, Double> p) throws ExcepcionsRecomanador{
        FactoriaCtrl fc = FactoriaCtrl.getInstance(); 
        CtrlDomini DominiCtrl = fc.getCtrlDomini();
        Double maxPunt = DominiCtrl.getPuntuacioMaxima();
        Double minPunt = DominiCtrl.getPuntuacioMinima();
        Double bonificacio = 1-((p.first-1)*0.025); //La bonificacion es para compensar las veces que el numero se repite
        Double nota = (p.second/p.first)/bonificacio;
        if (nota > maxPunt) return maxPunt;
        if (nota < minPunt) return minPunt;
        return nota;
    }

    //Pre: similitud >= 0 i notaItemSemblant >= 0
    //Post: Es retorna un double que es calcula a partir de la similitud i la notaItemSemblant
    private Double calcularNota(double similitud, double notaItemSemblant) throws ExcepcionsRecomanador{
        FactoriaCtrl fc = FactoriaCtrl.getInstance(); 
        CtrlDomini DominiCtrl = fc.getCtrlDomini();
        Double maxPunt = DominiCtrl.getPuntuacioMaxima();
        Double minPunt = DominiCtrl.getPuntuacioMinima();
    
        double desvTipus = (1- (similitud/maxPunt))*2.5;
        desvTipus = Math.random() * (desvTipus*maxPunt*0.4) - desvTipus;
        //desvTipus ha de ser un número entre -2.5 i +2.5 si la similitud es 0, i si la similitud és 5 serà 0
        if (notaItemSemblant + desvTipus > maxPunt) return maxPunt;
        else if (notaItemSemblant + desvTipus < minPunt) return minPunt;
        else return notaItemSemblant+desvTipus;
    }

    
}

//Classe feta per Joel Cardona i Daniel Pulido